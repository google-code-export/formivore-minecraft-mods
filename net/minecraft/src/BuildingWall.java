package net.minecraft.src;
/*
 *  Source code for the The Great Wall Mod and Walled City Generator Mods for the game Minecraft
 *  Copyright (C) 2011 by formivore

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * BuildingWall plans and builds a wall that flows along Minecraft's terrain.
 */

import java.lang.Math;

public class BuildingWall extends Building
{
	public final static int DEBUG=0;
	public final static boolean DEBUG_SIGNS=false;
	
	public final static int BUILDDOWN=12;
	public final static int SEARCHDOWN=2, MIN_SEARCHUP=2, MAX_SEARCHUP=5;
	public final static int DEFAULT_LOOKAHEAD=5, MIN_BRANCH_IMPROVEMENT=15;
	public final static int MAX_BACKTRACK_DEPTH=2;
	public final static int OVERHEAD_CLEARENCE=4, OVERHEAD_TREE_CLEARENCE=8;
	public final static int NO_GATEWAY=-1, NO_MIN_J=-1;
	private final static int MIN_GATEWAY_ROAD_LENGTH=20;
	
	//failCode values
	private final static int NO_FAIL=0, FAIL_OBSTRUCTED=1, FAIL_UNDERWATER=2, FAIL_TOO_STEEP_DOWN=3, FAIL_TOO_STEEP_UP=4,
	                         FAIL_HIT_WALL=5,FAIL_CANNOT_EXPLORE=6,FAIL_HIT_TARGET=7, FAIL_MAX_LENGTH=8;

	//**** WORKING VARIABLES **** 
	public int i1,j1,k1;
	public int n0=0;
	public int WalkHeight; //this is absolute, same as WallStyle
	public int maxLength;
	public int[] xArray, zArray;
	public int gatewayStart=NO_GATEWAY, gatewayEnd=NO_GATEWAY;
	public TemplateWall ws;

	public boolean target=false,circular=false;
	public int x_targ, z_targ, y_targ;
	public int minJ=NO_MIN_J;
	private boolean hitMaxDepth=false;
	public int failCode=NO_FAIL;
	public int endTLength=0; //length of end tower
	private int halfStairValue=2; //metavalue of half step based on bRule
	public int roofStyle;
	public TemplateRule towerRule,roofRule;
	public int Backtrack;

	//****************************************  CONSTRUCTORS - BuildingWall  *************************************************************************************//
	public BuildingWall(int ID_, WorldGeneratorThread wgt_,TemplateWall ws_,int dir_,int axXHand_, int maxLength_,boolean endTowers,int i1_,int j1_, int k1_){
		super(ID_,wgt_,ws_.rules[ws_.template[0][0][ws_.WWidth/2]],dir_,axXHand_, new int[]{ws_.WWidth,ws_.WHeight,0}, new int[]{i1_,j1_,k1_});
		constructorHelper(ws_,maxLength_,i1_,j1_,k1_);
		pickTowers(random.nextFloat() < ws.CircularProb,endTowers);
		Backtrack=wgt.BacktrackLength;
		if(maxLength>0){
			xArray[0]=0;
			zArray[0]=0;
		}
	}
	
	public BuildingWall(int ID_, WorldGeneratorThread wgt_,TemplateWall ws_,int dir_,int axXHand_, int maxLength_,boolean endTowers,int[] sourcePt){
		super(ID_,wgt_,ws_.rules[ws_.template[0][0][ws_.WWidth/2]],dir_,axXHand_, new int[]{ws_.WWidth,ws_.WHeight,0}, sourcePt);
		constructorHelper(ws_,maxLength_,sourcePt[0],sourcePt[1],sourcePt[2]);
		pickTowers(random.nextFloat() < ws.CircularProb,endTowers);
		Backtrack=wgt.BacktrackLength;
		if(maxLength>0){
			xArray[0]=0;
			zArray[0]=0;
		}
	}


	public BuildingWall(BuildingWall bw, int maxLength_,int i1_,int j1_, int k1_){
		super(bw.bID,bw.wgt, bw.bRule,bw.bDir,bw.bHand, new int[]{bw.bWidth,bw.bHeight,0}, new int[]{i1_,j1_,k1_});
		constructorHelper(bw.ws,maxLength_,i1_,j1_,k1_);
		Backtrack=bw.Backtrack;
		target=bw.target;
		x_targ=bw.x_targ;
		z_targ=bw.z_targ;
		y_targ=bw.y_targ;
	}
	
	private void constructorHelper(TemplateWall ws_,int maxLength_,int i1_,int j1_, int k1_){
		i1=i1_;
		j1=j1_;
		k1=k1_;
		ws=ws_;
		WalkHeight=ws.WalkHeight;
		maxLength=maxLength_;
		xArray=new int[maxLength];
		zArray=new int[maxLength];
		bLength=0;
		halfStairValue=blockToStepMeta(bRule.primaryBlock);
	}
	
	private void pickTowers(boolean circular_, boolean endTowers){
		circular=circular_;
		if(ws!=null){
			roofStyle=ws.pickRoofStyle(circular,random);
			towerRule=ws.TowerRule.getFixedRule(random);
			roofRule=ws.getRoofRule(circular);
			if(roofRule!=BuildingTower.RULE_NOT_PROVIDED) roofRule=roofRule.getFixedRule(random);
			endTLength=(endTowers && ws.MakeEndTowers) ? ws.pickTWidth(circular,random) : 0;
		}
	}

	
	//****************************************  FUNCTION  - setTowers *************************************************************************************//

	public BuildingWall setTowers(BuildingWall bw){
		circular=bw.circular;
		roofStyle=bw.roofStyle;
		towerRule=bw.towerRule;
		roofRule=bw.roofRule;
		endTLength=bw.endTLength;
		return this;
	}
	
	//****************************************  FUNCTION  - setMinJ *************************************************************************************//
	public BuildingWall setMinJ(int minJ_){
		minJ=minJ_;
		return this;
	}
	

	//****************************************  FUNCTION  - setCursor  *************************************************************************************//
	//Sets building class cursor to wall origin
	public void setCursor(int n){
		n0=n;
		if(n0>=0 && (n0<bLength || bLength==0)){
			setOriginLocal(i1,j1,k1,bLength==0 ? 0:xArray[n0],bLength==0 ? 0:zArray[n0],n0);
		}
	}
	
	//****************************************  FUNCTION  - getIJKPtAtN *************************************************************************************//
	public int[] getIJKPtAtN(int n, int x, int z, int y){
		if(n==n0) return getIJKPt(x,z,y);
		return getIJKPt(x+xArray[n]-xArray[n0],z+zArray[n]-zArray[n0],y+n-n0);
	}

	//****************************************  FUNCTION  - setTarget  *************************************************************************************//
	//Sets a target coordinate that the plan function can use to path towards
	//Will change EW and axY to reflect direction to target.
	//RETURNS; true if target is acceptable and reachable.
	public boolean setTarget(int[] targ){
		if( targ[1] > 20 && Math.abs(j1-targ[1]) < Math.max(Math.abs(i1-targ[0]),Math.abs(k1-targ[2])) ){
			target=true;
			setPrimaryAx( Math.abs(i1-targ[0]) > Math.abs(k1-targ[2])
						  	? (targ[0] > i1 ? DIR_EAST:DIR_WEST) 
						    : (targ[2] > k1 ? DIR_SOUTH:DIR_NORTH));
			setCursor(0);
			x_targ=getX(targ);
			z_targ=getZ(targ);
			y_targ=getY(targ);
			if(DEBUG>1) System.out.println("Set target for "+IDString()+"to "+globalCoordString(x_targ,z_targ,y_targ)+"!");
		}
		else System.out.println("Could not set target for "+IDString());
		return target;
	}
	
	public boolean ptIsToXHand(int[] pt, int buffer){
		setCursor(0);
		if(ws.TowerXOffset < 0) buffer-=ws.TowerXOffset;
		int ptY=getY(pt);
		if(ptY<0) return getX(pt)>=buffer;
		if(ptY>=bLength) return getX(pt)>=xArray[bLength-1]+buffer;
		return getX(pt)>=xArray[ptY]+buffer;
	}


	//****************************************  FUNCTION  - plan *************************************************************************************//
	//ASSUMPTIONS:
	//xarray and zarray contain planned values up to startN-1 inclusive.
	//RETURNS:
	//Length of new wall planned.
	//SIDE EFFECTS:
	//planL set to total length now planned.
	//xarry and zarry are filled up to planL.
	//hitMaxDepth true if planning was terminated due to depth==MAX_BACKTRACK_DEPTH.
	//failString contains termination rationale.
	public int plan(int startN, int depth, int lookahead, boolean stopAtWall) throws InterruptedException {
		if(startN<1 || startN >=maxLength) {System.err.println("Error, bad start length at BuildingWall.plan:"+startN+"."); return 0; }
		int fails=0;
		
		
		
		//i1=i0+EW*xArray[bLength-1]*axX+NS*bLength*axY;
		//j1=j0+zArray[startN-1];
		//k1=k0+NS*xArray[bLength-1]*axX+EW*bLength*axY;
		setOriginLocal(i1,j1,k1,xArray[startN-1],zArray[startN-1],startN);
		bLength=startN;
		
		
		if(DEBUG>1 && depth > 0) System.out.println("planWall "+IDString()+", depth="+depth+" n="+startN+" maxlLen="+maxLength+" at ("+i0+","+j0+","+k0+")");
		//int searchUp=Math.min(Math.max(MIN_SEARCHUP,WalkHeight+1),MAX_SEARCHUP);
		int searchUp=MIN_SEARCHUP;
		int obstructionHeight=WalkHeight >4 ? WalkHeight+1 : bHeight+1;

		while(true){
			int gradx=0,gradz=0;
			failCode=NO_FAIL;

			//query the exploration handler to see if we have reached limit, if so then terminate
			if(!(queryExplorationHandler(-1,0,0) && queryExplorationHandler(bWidth,0,0))) {
				failCode=FAIL_CANNOT_EXPLORE;
				break; 
			}

			for(int x1=-1; x1<bWidth+1;x1++){
				for(int z1=-SEARCHDOWN; z1<=searchUp; z1++){
					int blockId=getBlockIdLocal(x1, z1, 0);
					if(!IS_WALLABLE[blockId]){
						gradz++;
						gradx+=signum(2*x1-bWidth+1); 
					}
					else if(IS_LIQUID_BLOCK[blockId])
						gradx-=signum(2*x1-bWidth+1);

					//hit another wall, want to ignore sandstone that appears naturally in deserts
					if((stopAtWall || z1 < -2) && isArtificialWallBlock(x1,z1,0))
						failCode=FAIL_HIT_WALL;
				}
				if(IS_LIQUID_BLOCK[getBlockIdLocal(x1,ws.waterHeight+1,0)])
					failCode=FAIL_UNDERWATER;
				if(!isWallable(x1,obstructionHeight,0) && failCode==NO_FAIL)
					failCode=FAIL_OBSTRUCTED;
				
			}

			gradz=(gradz+(bWidth+2)/2)/(bWidth+2)-SEARCHDOWN;
			if(failCode==FAIL_HIT_WALL) gradz=0;
			if(failCode==NO_FAIL && gradz < -1) failCode=FAIL_TOO_STEEP_DOWN;
			if(failCode==NO_FAIL && gradz > 4) failCode=FAIL_TOO_STEEP_UP;
			
			gradz=signum(gradz, 0);
			if(minJ!=NO_MIN_J && zArray[bLength-1]+gradz+j1 < minJ) gradz=0; //don't go below minJ
			if(gradz==0){
				int HorizForceThreshold=bWidth/2;
				int bias= target ? signum(xArray[bLength-1]-x_targ)*(2*HorizForceThreshold) : 0;
				gradx= (gradx > HorizForceThreshold + bias ? 1 : (gradx < -HorizForceThreshold + bias ? -1 : 0));
			} else gradx=0;
			
			setOriginLocal(i0,j0,k0,gradx,gradz,1);
			
			xArray[bLength]=xArray[bLength-1]+gradx;
			zArray[bLength]=zArray[bLength-1]+gradz;
			bLength++;


			//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%   TERMINATION / BACKTRACKING   %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
			if(failCode==NO_FAIL) fails=0;
			else fails++;

			if(target && bLength > y_targ){
				failCode=FAIL_HIT_TARGET;
				break;
			}
			else if(bLength>=maxLength){
				failCode=FAIL_MAX_LENGTH;
				break;
			}
			else if(failCode==FAIL_HIT_WALL || failCode==FAIL_UNDERWATER){
				bLength-=fails;
				break;
			}
			else if(fails>=lookahead){
				bLength-=fails; //planL should be at first failed position at end of loop

				if( bLength-startN < Backtrack || (bLength-startN < MIN_BRANCH_IMPROVEMENT && depth!=0) ){
					break; //loop termination condition 2
				}
				if(depth >= MAX_BACKTRACK_DEPTH ){ //loop termination condition 2
					hitMaxDepth=true; //may still be able to proceed, note this so we can do so from root
					break; //loop termination condition 3
				}
				else{
					if(DEBUG>1) System.out.println("\nTrying branches for "+IDString()+", depth="+depth+" at n="+bLength+" x="+(xArray[bLength])+" z="+(zArray[bLength]));
					int improvement, bestImprovement=0;
					BuildingWall branch, bestBranch=null;
					//String[] branchNames={"Down","Minus","Straight","Plus","Up"};

					for(int zAx=0;zAx<=1;zAx++){
						for(int d=-1;d<=1;d++){
							if(!(zAx==0 && d==0)){


								branch=new BuildingWall(this, maxLength, i1, j1, k1);
								for(int m=0;m<Backtrack;m++){
									branch.xArray[bLength-Backtrack+m]=xArray[bLength-Backtrack]+(1-zAx)*(d*m);
									branch.zArray[bLength-Backtrack+m]=zArray[bLength-Backtrack]+zAx*(d*m);
								}
								improvement=branch.plan(bLength, depth+1,lookahead, stopAtWall);
								if(improvement > bestImprovement){
									bestBranch=branch;
									bestImprovement=improvement;
								}

							}
						}
					}
					if(bestImprovement+bLength>maxLength) bestImprovement=maxLength-bLength;
					if(bestImprovement > 0){
						//System.out.println("Chose branch="+bestBranch.branchName+" for wall "+IDString()+"depth="+depth+" at n="+planL+" with added length="+bestImprovement);
						for(int m=bLength-Backtrack;m<bLength+bestImprovement;m++){
							xArray[m]=bestBranch.xArray[m];
							zArray[m]=bestBranch.zArray[m];
							//failString=bestBranch.failString;
							failCode=bestBranch.failCode;

						}
						hitMaxDepth=bestBranch.hitMaxDepth;
						bLength+=bestImprovement;
					}
					else if(DEBUG>1) System.out.println("Could not improve wall "+IDString()+" at n="+bLength+"\n");

					if(depth==0 && hitMaxDepth && bLength<maxLength){
						hitMaxDepth=false;
						fails=1;
						if(DEBUG>1) System.out.println("Hit max search depth, continuing planning wall "+IDString()+"at n="+bLength+" from root");
					}
					else break; //we have added branches if any and did not hit max depth, so break
				}
				//if(DEBUG && planL>startN) printWall(startN);
			}
		}//end main loop
		
		if(depth==0){
			bLength-=endTLength;
			if(bLength<startN) bLength=startN;
		}

		setCursor(0);
		//wgt.explorationHandler.releaseChunks(chunksExplored);
		return bLength-startN;
	}
	
	public boolean queryLayout(int layoutCode){
		for(int n=0;n<bLength;n++){
			setCursor(n);
			if(!wgt.layoutIsClear(getIJKPt(0,0,0), getIJKPt(bWidth-1,0,0),layoutCode)){
				setCursor(0);
				return false;
			}
		}
		setLayoutCode(layoutCode);
		return true;
	}
	
	public void setLayoutCode(int layoutCode){
		for(int n=0;n<bLength;n++){
			setCursor(n);
			wgt.setLayoutCode(getIJKPt(0,0,0), getIJKPt(bWidth-1,0,0), layoutCode);
		}
		setCursor(0);
	}

	//****************************************  FUNCTION - buildFromTML*************************************************************************************//
	//Builds a planned wall from a template
	public void buildFromTML(){
		if(ws==null){ 
			System.out.println("Tried to build wall from template but no template was given!");
			return; 
		}
		setCursor(0);

		if(DEBUG>0){
			if(bLength>0) System.out.println("**** Built "+ws.name+" wall "+IDString()+", length " + (bLength) + " from "+globalCoordString(xArray[0], zArray[0], 0)+
					" to "+globalCoordString(xArray[bLength-1], zArray[bLength-1], bLength-1)+" ****");
			else System.out.println("**** Wall too short to build! "+IDString()+"length="+bLength+" at "+globalCoordString(0,0,0)+" ****");
			System.out.println("Wall planning was terminated due to: "+failString()+"\n");
		}

		int lN=0;
		int[] idAndMeta;
		int layer[][];

		//get named layers
		int base[]=ws.template[0][0]; //defaults to bottom line of first layer
		if(ws.namedLayers.containsKey("base")) base=(ws.namedLayers.get("base"))[ws.length-1];
		int[][] shifted, shiftedLeft, shiftedRight, shiftedUp, shiftedDown;
		shifted=ws.namedLayers.containsKey("shifted") ? ws.namedLayers.get("shifted") : ws.template[0];
		shiftedLeft=ws.namedLayers.containsKey("shifted_left") ? ws.namedLayers.get("shifted_left") : shifted;
		shiftedRight=ws.namedLayers.containsKey("shifted_right") ? ws.namedLayers.get("shifted_right") : shifted;
		shiftedUp=ws.namedLayers.containsKey("shifted_up") ? ws.namedLayers.get("shifted_up") : shifted;
		shiftedDown=ws.namedLayers.containsKey("shifted_down") ? ws.namedLayers.get("shifted_down") : shifted;


		for(setCursor(0); n0<bLength; setCursor(n0+1)){	
			if(n0==0) layer=shifted;
			else if(xArray[n0-1]<xArray[n0]) layer=shiftedRight;
			else if(xArray[n0-1]>xArray[n0]) layer=shiftedLeft;
			else if(zArray[n0-1]<zArray[n0]) layer=shiftedUp;
			else if(zArray[n0-1]>zArray[n0]) layer=shiftedDown;
			else if(n0==bLength-1 || xArray[n0+1]!=xArray[n0] || zArray[n0+1]!=zArray[n0]) layer=shifted;
			else layer=ws.template[lN];

			if(layer==ws.template[lN]) lN=(lN+1) % ws.height;
			else lN=0;

			//wall
			for(int x1=0; x1<bWidth;x1++){
				boolean keepWallFromAbove=true;
				for(int z1=bHeight+OVERHEAD_CLEARENCE-1; z1>=-ws.embed; z1--){
					boolean wallBlockPresent=isWallBlock(x1,z1,0);
					idAndMeta= z1<bHeight 
								? ws.rules[layer[z1+ws.embed][x1]].getBlockOrHole(random) 
								: HOLE_BLOCK;

					//starting from top, preserve old wall block until we run into a non-wall block
					if(keepWallFromAbove && wallBlockPresent && (idAndMeta[0]==AIR_ID || idAndMeta[0]==HOLE_ID)){
						continue;
					} else keepWallFromAbove=false;
										
					if(idAndMeta[0]==WALL_STAIR_ID){
						if(!wallBlockPresent && !IS_LIQUID_BLOCK[getBlockIdLocal(x1,z1,0)]){
							if(n0>0 && zArray[n0-1]>zArray[n0]){  //stairs, going down
								if((n0==1 || zArray[n0-2]==zArray[n0-1]) && (n0==bLength-1 || zArray[n0]==zArray[n0+1]))
									setBlockLocal(x1, z1, 0, STEP_ID, idAndMeta[1]);
								else setBlockLocal(x1, z1, 0, STEP_TO_STAIRS[idAndMeta[1]],2);
							}
							else if(n0<bLength-1 && zArray[n0]<zArray[n0+1]){ //stairs, going up
								if((n0==0 || zArray[n0-1]==zArray[n0]) && (n0==bLength-2 || zArray[n0+1]==zArray[n0+2]))
									setBlockLocal(x1, z1, 0, STEP_ID, idAndMeta[1]);
								else setBlockLocal(x1, z1, 0, STEP_TO_STAIRS[idAndMeta[1]],3);
							}
							else setBlockLocal(x1,z1,0,HOLE_ID);
						}
					}else{ //not a stair
						// if merging walls, don't clutter with crenelations etc.
						if(z1>=WalkHeight && ( x1==0 &&        (wallBlockPresent || isWallBlock(-1,WalkHeight-1,0) || isWallBlock(-1,WalkHeight-2,0)) 
						                 ||    x1==bWidth-1 && (wallBlockPresent || isFloor(bWidth,WalkHeight-1,0) || isWallBlock(bWidth,WalkHeight-2,0))) ){  
							continue;
						}
						
						if(idAndMeta[0]==HOLE_ID && z1<bHeight) setBlockWithLightingLocal(x1,z1,0,HOLE_ID,0,true); //force lighting update for holes
						else setBlockLocal(x1,z1,0,idAndMeta);  //straightforward build from template
					}
					
				}
			}
			//base
			for(int x1=0; x1<bWidth;x1++) 
				buildDown(x1,-1-ws.embed,0,ws.rules[base[x1]],ws.leveling,Math.min(2,ws.embed),3);


			clearTrees();
			mergeWallLayer();

			//DEBUGGING, creates signs with ID/distance info
			if(DEBUG_SIGNS && (n0) % 10==0){
				//String[] lines=new String[]{IDString().split(" ")[0],IDString().split(" ")[1],"Dist:"+n+ " / "+planL,globalCoordString(1,WalkHeight,0)};
				String[] lines=new String[]{IDString().split(" ")[0],j1+"","Dist:"+n0+ " / "+bLength,globalCoordString(1,WalkHeight,0)};
				setSignOrPost(1,WalkHeight,0,true,8,lines);
				setSignOrPost(-1,WalkHeight-1,0,false,3,lines);
				setSignOrPost(bWidth,WalkHeight-1,0,false,2,lines);
			}
			
			flushDelayed();

		}

		setCursor(0);
	}

	//****************************************  FUNCTION - clearTrees *************************************************************************************//
	private void clearTrees(){
		for(int x1=0; x1<bWidth;x1++)
			for(int z1=bHeight+OVERHEAD_CLEARENCE; z1<bHeight+OVERHEAD_TREE_CLEARENCE; z1++)
				if(getBlockIdLocal(x1, z1, 0)==LOG_ID || getBlockIdLocal(x1, z1, 0)==LEAVES_ID || getBlockIdLocal(x1, z1, 0)==SNOW_ID )
					setBlockLocal(x1, z1, 0, AIR_ID); //kill trees aggressively
	}

	//****************************************  FUNCTION - mergeWallLayer *************************************************************************************//
	private void mergeWallLayer(){
		//if side is a floor one below, add a step down
		if(isFloor(-1,WalkHeight-1,0))   setBlockLocal(-1, WalkHeight-1, 0, STEP_ID, halfStairValue);
		if(isFloor(bWidth,WalkHeight-1,0))   setBlockLocal(bWidth, WalkHeight-1, 0, STEP_ID, halfStairValue);
		
		//      x
		// if  xxo are floors one above, add a step up
		//      x
		if(isFloor(-1,WalkHeight+1,0)  && isFloor(-2,WalkHeight+2,0) && isFloor(-2,WalkHeight+2,1)  && isFloor(-2,WalkHeight+2,-1)) 
			setBlockLocal(0, WalkHeight, 0, STEP_ID,halfStairValue);
		if(isFloor(bWidth,WalkHeight+1,0)  && isFloor(bWidth+1,WalkHeight+2,0) && isFloor(bWidth+1,WalkHeight+2,1)  && isFloor(bWidth+1,WalkHeight+2,-1)) 
			setBlockLocal(bWidth-1, WalkHeight, 0, STEP_ID, halfStairValue);

		//clean up stairs descending into this wall
		int[] pt=getIJKPt(-1,WalkHeight-1,0);
		if(IS_STAIRS_BLOCK[world.getBlockId(pt[0],pt[1],pt[2])] && STAIRS_META_TO_DIR[world.getBlockMetadata(pt[0],pt[1],pt[2])]==rotDir(bDir,-bHand))
			world.setBlock(pt[0],pt[1],pt[2], stairToSolidBlock(world.getBlockId(pt[0],pt[1],pt[2])));
		pt=getIJKPt(bWidth,WalkHeight-1,0);
		if(IS_STAIRS_BLOCK[world.getBlockId(pt[0],pt[1],pt[2])] && STAIRS_META_TO_DIR[world.getBlockMetadata(pt[0],pt[1],pt[2])]==rotDir(bDir,bHand))
			world.setBlock(pt[0],pt[1],pt[2], stairToSolidBlock(world.getBlockId(pt[0],pt[1],pt[2])));
	}


	//****************************************  FUNCTION - buildTowers *************************************************************************************//

	public void buildTowers(boolean lSideTowers,boolean rSideTowers, boolean gatehouseTowers, boolean overlapTowers, boolean isAvenue) throws InterruptedException{
		if(ws==null){
			System.out.println("Tried to build towers but wall style was null!");
			return;
		}
		if(!ws.MakeBuildings) return;

		int cursorStart=Math.max(ws.getTMaxWidth(circular)+3,2*ws.BuildingInterval/3);
		for(setCursor(cursorStart); n0<bLength; setCursor(n0+1)){
			
			if(gatewayStart!=NO_GATEWAY && n0>=gatewayStart && n0<=gatewayEnd+ws.getTMaxWidth(circular)+2){ 
				//don't built if there's a gateway
				setCursor(gatewayEnd+ws.getTMaxWidth(circular)+2);
				if(n0>=bLength) break;
			}
			
			//towers are built from n1-2 to n1-tw-1
			int tw=ws.pickTWidth(circular,random);
			int tl=circular ? tw : ws.pickTWidth(circular,random);
			int twrNMid=n0-tw/2-2;
			int clearSide=-bHand*signum(curvature(xArray[n0-tw-3], xArray[twrNMid], xArray[n0], 0),0);
			if(clearSide==0){
				if(lSideTowers && rSideTowers) clearSide=2*random.nextInt(2)-1;
				else clearSide= lSideTowers ? L_HAND : R_HAND;
			}
			
			//try tower types
			if(gatehouseTowers && ws.DefaultTowerWeight>0 && !circular && curvature(zArray[n0-tw-3], zArray[twrNMid], zArray[n0], 0)==0 
															   && curvature(xArray[n0-tw-3], xArray[twrNMid], xArray[n0], 2)==0){
				
				if(DEBUG>1) System.out.println("Building gatehouse for "+IDString()+" at n="+n0+" "+globalCoordString(0,0,0)+" width "+tw);
				BuildingTower tower = new BuildingTower(bID+n0, this, flipDir(bDir), -bHand, tw, ws.pickTHeight(circular,random), tl, 
						                                getIJKPtAtN(twrNMid,bWidth/2-tw/2,0,tw/2));
				if(!tower.isObstructedRoof(-1)){
					wgt.setLayoutCode(tower.getIJKPt(0,0,0),tower.getIJKPt(tw-1,0,tw-1), WorldGeneratorThread.LAYOUT_CODE_TOWER);
					tower.build(xArray[n0-1]-xArray[twrNMid], xArray[n0-tw-2]-xArray[twrNMid], false);
					
					setCursor(n0+ws.BuildingInterval-1);
				}
			}
			else if((lSideTowers && clearSide==L_HAND) || (rSideTowers && clearSide==R_HAND)) {   //side towers
				if(DEBUG>1) System.out.println("Building side tower for "+IDString()+" at n="+n0+" "+globalCoordString(0,0,0)+" with clearSide="+clearSide+" width "+tw);
				TemplateTML building=ws.buildings.get(Building.selectWeightedOption(random,ws.buildingWeights[0],ws.buildingWeights[1]));
			
				/*
				tw=15;
				byte[][] caRule=BuildingCellularAutomaton.parseCARule("B36/S013468",null);
				TemplateRule ghastTowerRule=new TemplateRule(new int[]{NETHER_BRICK_ID}, new int[]{0},100);
				for(int tries=0; tries < 10; tries++){
					byte[][] seed = BuildingCellularAutomaton.makeSymmetricSeed(8,8,0.5F,random);
					BuildingCellularAutomaton bca=new BuildingCellularAutomaton(wgt,ghastTowerRule,DIR_NORTH,1,tw, 25+random.nextInt(10),tw, 12,seed,caRule,
																				getIJKPtAtN(twrNMid, clearSide==bHand ? bWidth:-1, 0, tw/2));
					if(bca.plan() && bca.queryCanBuild(0)){
						bca.build();
						break;
					}
				}
				*/
				
				
				if(building==TemplateWall.DEFAULT_TOWER){
					int ybuffer=(isAvenue ? 0:1) - ws.TowerXOffset;
					BuildingTower tower=new BuildingTower(bID+n0,this, rotDir(bDir,clearSide), clearSide, tw, ws.pickTWidth(circular,random), tl, 
														  getIJKPtAtN(twrNMid, clearSide==bHand ? (bWidth - ybuffer):ybuffer-1, 0, tw/2));
					if(tower.queryCanBuild(ybuffer,overlapTowers)){
						tower.build(0,0,true);
						setCursor(n0+ws.BuildingInterval-1);
					}
				}
				else{
					BuildingTML buildingTML=new BuildingTML(bID+n0,wgt,rotDir(bDir,clearSide),clearSide,building,
															getIJKPtAtN(twrNMid, clearSide==bHand ? bWidth:-1, 0, building.length/2));
					if(buildingTML.queryCanBuild(0)){
						buildingTML.build();
						setCursor(n0+ws.BuildingInterval-1);
					}
				}
				
			}
		}
		setCursor(0);

		//build towers at endpoints
		if(endTLength >= BuildingTower.TOWER_UNIV_MIN_WIDTH){
			//so circular towers fit better, they will be placed at y=bLength-1. Square towers are at y=bLength.
			int endTN = circular ? bLength-2:bLength-1;
			if(endTN<0) endTN=0;
			
			for(int tl=endTLength; tl>=ws.getTMinWidth(circular); tl--){
				int tw=circular ? tl : ws.pickTWidth(false,random);
				int[] pt=getIJKPtAtN(endTN, bWidth/2-tw/2, 0, 1);
				
				BuildingTower tower=new BuildingTower(bID+bLength,this,bDir,bHand,tw,ws.pickTHeight(circular,random),tl,pt);
				if(tower.queryCanBuild(1,overlapTowers)){
					tower.build(0,0,true);
					break;
				}
			}
		}
		
	}







	//****************************  FUNCTION  - buildGateway  *************************************************************************************//
	//Builds a gateway and road on one side of gateway. Call after build() and before buildTowers().
	//
	//PARAMETERS:
	//startScan,endScan - bounds of where to look to place gateway
	//gateHeight, gateWidth - dimensions of the gateway in the wall
	//rs - wall style of avenues
	//flankTHand - the hand to build flanking towers on. 0 => n0 flanking towers.
	//XMaxLen, antiXMaxLen - maximum length of avenues for the +X and -X side avenues
	//XTarget, antiXTarget - the target point for the +X and -X side avenues
	//XHand, antiXHand - the internal handedness of the +X and -X side avenues.
	//
	//RETURNS:
	//y-position where gateway was build or -1 if no gateways was built
	//
	public BuildingWall[] buildGateway(int startScan, int endScan, int gateHeight,int gateWidth,TemplateWall rs,int flankTHand,
			int XMaxLen,int[] XTarget,int XHand, int antiXMaxLen, int[] antiXTarget, int antiXHand) throws InterruptedException {
		BuildingWall[] avenues=null;
		if(rs!=null) gateWidth=rs.WWidth;
		
		for(int m=0; Math.abs(m)<(endScan-startScan)/2; m=-m+(m<=0 ? 1:0)){
			setCursor((endScan + startScan)/2+m);
			if(curvature(zArray[n0], zArray[n0-gateWidth/2], zArray[n0-gateWidth-1], 1)==0 &&
			   curvature(xArray[n0], xArray[n0-gateWidth/2], xArray[n0-gateWidth-1], 0)==0)
			{
				int tw=ws.pickTWidth(circular,random), th=ws.getTMaxHeight(circular);
				if(rs!=null){ 
					avenues=new BuildingWall[]{ new BuildingWall(bID,wgt,rs,rotDir(bDir,bHand),XHand, XMaxLen,false,getIJKPt(bWidth,0,XHand==-bHand ? 1-gateWidth :0))
					         ,new BuildingWall(bID,wgt,rs,rotDir(bDir,-bHand),antiXHand, antiXMaxLen,false,getIJKPt(-1,0,antiXHand==bHand ? 1-gateWidth :0))};

					avenues[0].setTarget(XTarget==null ? getIJKPt(bWidth+tw,0,XHand==-bHand ? 1-gateWidth :0) : XTarget);
					avenues[0].plan(1,0,DEFAULT_LOOKAHEAD,true);
					if(XTarget==null && avenues[0].bLength>=tw){
						avenues[0].target=false;
						avenues[0].plan(tw+1,0,DEFAULT_LOOKAHEAD,true);
					}
			
					if(avenues[0].bLength>=MIN_GATEWAY_ROAD_LENGTH){
						avenues[1].setTarget(antiXTarget==null ? getIJKPt(-1-tw,0,antiXHand==bHand ? 1-gateWidth :0) : antiXTarget);
						avenues[1].plan(1,0,DEFAULT_LOOKAHEAD,true);
						if(antiXTarget==null && avenues[1].bLength>=tw){
							avenues[1].target=false;
							avenues[1].plan(tw+1,0,DEFAULT_LOOKAHEAD,true);
						}
					}
				}
				
				//build it
				//gateway is built from n-gatewayWidth to n-1
				
				if(rs==null ||  avenues[1].bLength>=MIN_GATEWAY_ROAD_LENGTH){ 
					if(rs!=null){
						avenues[0].smooth(10,10,false);
						avenues[1].smooth(10,10,false);
					}
					gateHeight=Math.min(gateHeight,bHeight-1);
					for(int x1=0;x1<bWidth;x1++)
						for(int y1=0;y1>-gateWidth;y1--)
							for(int z1=0;z1<gateHeight;z1++)
								if(!((y1==0 || y1==1-gateWidth) && z1==gateHeight-1))
									setBlockLocal(x1,z1,y1,AIR_ID);
					if(flankTHand!=-bHand ) setBlockLocal(-1,gateHeight-2,-gateWidth,WEST_FACE_TORCH_BLOCK);
					if(flankTHand!=-bHand ) setBlockLocal(-1,gateHeight-2,1,WEST_FACE_TORCH_BLOCK);
					if(flankTHand!=bHand ) setBlockLocal(bWidth,gateHeight-2,-gateWidth,EAST_FACE_TORCH_BLOCK);
					if(flankTHand!=bHand ) setBlockLocal(bWidth,gateHeight-2,1,EAST_FACE_TORCH_BLOCK);
					
					//build flanking towers
					if(n0+gateWidth+tw > bLength) flankTHand=0;
					if(flankTHand!=0){
						int tnMid1=n0-gateWidth-tw/2;
						int tnMid2=n0+tw/2;
						int x1=flankTHand==bHand ? bWidth-1+ws.TowerXOffset : -ws.TowerXOffset;
						
						//preceding tower
						new BuildingTower(0,this, rotDir(bDir,flankTHand), bHand, tw, th, tw, 
											getIJKPtAtN(tnMid1, x1, 0, tw/2)).build(0,0,false);
						//following tower
						new BuildingTower(0,this, rotDir(bDir,flankTHand),-bHand, tw, th+zArray[tnMid1]-zArray[tnMid2], tw,
											getIJKPtAtN(tnMid2, x1, 0, 1-tw/2)).build(0,0,false);
					}
					flushDelayed();
					
					
					gatewayStart=n0+1-gateWidth-(flankTHand!=0 ? tw+ws.BuildingInterval/2:0);
					gatewayEnd=n0+(flankTHand!=0 ? (tw+ws.BuildingInterval/2):0);
					return avenues;
				}
			}

		}
		return null;
	}
	

	//=====================================================  HELPER FUNCTIONS ==========================================================================================================//   

	//****************************************  FUNCTION  - curvature  *************************************************************************************//
	//curvature(LinkedList ll, int a, int b, int c, int wiggle)
	//wiggle allows for some leeway before slope is detected
	//RETURNS: 0 if constant (000)
	//         1 if concave up (+0+),(00+),(+00)
	//        -1 if concave down (-0-),(-00),(00-)
	//         2 if increasing (-0+)
	//        -2 if decreasing (+0-)
	private static int curvature(int a, int b, int c, int wiggle){
		int d1=signum(a-b,wiggle);
		int d2=signum(c-b,wiggle);
		if(d1*d2<0) return 2*d2;
		return signum(d1+d2,0);
	}

	//****************************************  FUNCTION  - failString  *************************************************************************************//
	public String failString(){
		switch(failCode) {
		case FAIL_OBSTRUCTED: return  "Obstructed.";
		case FAIL_UNDERWATER: return "Underwater.";
		case FAIL_TOO_STEEP_DOWN: return "Too Steep Down.";
		case FAIL_TOO_STEEP_UP: return "Too Steep Up.";
		case FAIL_HIT_WALL: return "Hit Wall";
		case FAIL_CANNOT_EXPLORE: return "Could not explore";
		case FAIL_HIT_TARGET: return "Hit Target";
		case FAIL_MAX_LENGTH: return "Max length ("+maxLength+") reached.";
		}
		return "No Fail.";
	}


	//****************************************  FUNCTION  - printWall  *************************************************************************************//
	public void printWall(){
		printWall(0);
	}


	public void printWall(int start){
		System.out.println("Printing "+IDString()+" wall from n="+start+" to n="+(bLength-1));
		for(int m=start; m<bLength; m++) {
			if(m %10==0) System.out.print("|");
			if(m %100==0) System.out.print("||");
			System.out.print(xArray[m]+",");
			if(m>0 && Math.abs(xArray[m]-xArray[m-1])>1 )
				System.out.print("(ERROR: X-slope>1 at n="+m+")");

		}
		System.out.print("\n");
		for(int m=start; m<bLength; m++) {
			if(m %10==0) System.out.print("|");
			if(m %100==0) System.out.print("||");
			System.out.print(zArray[m]+",");
			if(m>0 && Math.abs(zArray[m]-zArray[m-1])>1 )
				System.out.print("(ERROR: Z-slope>1 at n="+m+")");
		}
		System.out.println("\n");
	}

	//****************************************  FUNCTION - smooth  *************************************************************************************//
	public void smooth(int convexWindow, int concaveWindow,  boolean flattenEnds){
		smooth(xArray,0,bLength-1,convexWindow,concaveWindow, flattenEnds);
		smooth(zArray,0,bLength-1,convexWindow,concaveWindow, flattenEnds);
	}
	
	//smooth(int[] arry, int a, int b,int convexWindow, int concaveWindow)
	public static void smooth(int[] arry, int a, int b,int convexWindow, int concaveWindow, boolean flattenEnds){
		int n, convexWinStart=a, concaveWinStart=a, smoothStart=-1, leadSlope;
		for(int winEnd=a+2;winEnd<=b;winEnd++){
			if(winEnd>=a+convexWindow) convexWinStart++;
			if(winEnd>=a+concaveWindow) concaveWinStart++;
			n=winEnd-1;
			leadSlope=arry[winEnd] - arry[n];

			//check the smaller window in both directions, and the larger only in the given direction
			if(leadSlope*(arry[n] - arry[Math.max(convexWinStart,concaveWinStart)])<0 )
				smoothStart=Math.max(convexWinStart,concaveWinStart);
			else if( leadSlope*(arry[n] - arry[Math.min(convexWinStart,concaveWinStart)])<0 && leadSlope*(convexWindow-concaveWindow) > 0)
				smoothStart=Math.min(convexWinStart,concaveWinStart);
			else
				smoothStart=-1;
			if(smoothStart>=0){
				if(DEBUG>1){
					System.out.print("smoothing: ");
					for(int m=smoothStart; m<= winEnd; m++) System.out.print(arry[m]+",");
					System.out.println();
				}
				do {
					//System.out.println("smoothing n="+n+" "+arry[n-1] +" "+ arry[n] +" "+ arry[winEnd]);
					arry[n]=arry[winEnd];
					n--;
				} while(n>smoothStart&& arry[n]!=arry[winEnd]);
			}

		}
		
		//flatten ends if they end on slopes
		if(flattenEnds){
			if(b>a && arry[a]!=arry[a+1]) arry[a]=arry[a+1];
			if(b>a && arry[b]!=arry[b-1]) arry[b]=arry[b-1];
		}
	}

}

