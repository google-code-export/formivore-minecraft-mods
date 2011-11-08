package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Builds walls with optional towers and gateways
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
	private final static int NO_FAIL=0, FAIL_OBSTRUCTED=1, FAIL_UNDERWATER=2, FAIL_TOO_STEEP_DOWN=3, FAIL_TOO_STEEP_UP=4, FAIL_HIT_WALL=5,
							 FAIL_CANNOT_EXPLORE=6,FAIL_HIT_TARGET=7, FAIL_MAX_LENGTH=8;

	//**** WORKING VARIABLES **** 
	public int i0,j0,k0;
	//public int bHeight, bWidth;
	public int WalkHeight; //this is absolute, same as WallStyle
	public int maxLength;
	public int[] xArray, zArray;
	public int gatewayStart=NO_GATEWAY, gatewayEnd=NO_GATEWAY;
	public WallStyle ws;

	public boolean target=false,circular=false;
	public int x_targ, z_targ, y_targ;
	public int minJ=NO_MIN_J;
	private boolean hitMaxDepth=false;
	public int failCode=NO_FAIL;
	public int endTLength=0;
	private int halfStairValue=2;
	public int roofStyle;
	public TemplateRule towerRule,roofRule;
	public int Backtrack;

	//****************************************  CONSTRUCTORS - BuildingWall  *************************************************************************************//
	public BuildingWall(int ID_, WorldGeneratorThread wgt_,WallStyle ws_,int dir_,int axXHand_, int maxLength_,boolean endTowers,int i0_,int j0_, int k0_){
		super(ID_,wgt_,ws_.rules[ws_.template[0][0][ws_.WWidth/2]],dir_,axXHand_, new int[]{ws_.WWidth,ws_.WHeight,0}, new int[]{i0_,j0_,k0_});
		constructorHelper(ws_,maxLength_,i0_,j0_,k0_);
		pickTowers(random.nextFloat() < ws.CircularProb,endTowers);
		Backtrack=wgt.explorationHandler.Backtrack;
		if(maxLength>0){
			xArray[0]=0;
			zArray[0]=0;
		}
	}
	
	public BuildingWall(int ID_, WorldGeneratorThread wgt_,WallStyle ws_,int dir_,int axXHand_, int maxLength_,boolean endTowers,int[] pt){
		super(ID_,wgt_,ws_.rules[ws_.template[0][0][ws_.WWidth/2]],dir_,axXHand_, new int[]{ws_.WWidth,ws_.WHeight,0}, pt);
		constructorHelper(ws_,maxLength_,pt[0],pt[1],pt[2]);
		pickTowers(random.nextFloat() < ws.CircularProb,endTowers);
		Backtrack=wgt.explorationHandler.Backtrack;
		if(maxLength>0){
			xArray[0]=0;
			zArray[0]=0;
		}
	}


	public BuildingWall(BuildingWall bw, int maxLength_,int i0_,int j0_,int k0_){
		super(bw.bID,bw.wgt, bw.bRule,bw.bDir,bw.bHand, new int[]{bw.bWidth,bw.bHeight,0}, new int[]{i0_,j0_,k0_});
		constructorHelper(bw.ws,maxLength_,i0_,j0_,k0_);
		Backtrack=bw.Backtrack;
		target=bw.target;
		x_targ=bw.x_targ;
		z_targ=bw.z_targ;
		y_targ=bw.y_targ;
	}
	
	private void constructorHelper(WallStyle ws_,int maxLength_,int i0_,int j0_, int k0_){
		i0=i0_;
		j0=j0_;
		k0=k0_;
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
			endTLength=(endTowers && ws.EndTowers) ? ws.pickTWidth(circular,random) : 0;
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
		if(n>=0 && (n<bLength || bLength==0))
			setOrigin(i0+EW*axX*xArray[n]+NS*axY*n, j0+zArray[n], k0+NS*axX*xArray[n]+EW*axY*n);
		else
			System.out.println("ERROR:tried to set wall cursor out of bounds n="+n+" planL="+bLength);
	}

	//****************************************  FUNCTION  - setTarget  *************************************************************************************//
	//Sets a target coordinate that the plan function can use to path towards
	//Will change EW and axY to reflect direction to target.
	//RETURNS; true if target is acceptable and reachable.
	public boolean setTarget(int[] targ){
		if( targ[1] > 20 && Math.abs(j0-targ[1]) < Math.max(Math.abs(i0-targ[0]),Math.abs(k0-targ[2])) ){
			target=true;
			if(Math.abs(i0-targ[0]) > Math.abs(k0-targ[2])){
				setPrimaryAx( signum(targ[0]-i0,0)==1 ? DIR_SOUTH:DIR_NORTH );
				x_targ=axX*(targ[2]-k0);
				z_targ=targ[1]-j0;
				y_targ=axY*(targ[0]-i0);
			}
			else{
				setPrimaryAx( signum(targ[2]-k0,0)==1 ? DIR_WEST:DIR_EAST);
				x_targ=axX*(targ[0]-i0);
				z_targ=targ[1]-j0;
				y_targ=axY*(targ[2]-k0);
			}
			if(DEBUG>1){
				setCursor(0);
				System.out.println("Set target for "+IDString()+"to "+globalCoordString(x_targ,z_targ,y_targ)+"!");
			}
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
		bLength=startN;
		i1=i0+EW*xArray[bLength-1]*axX+NS*bLength*axY;
		j1=j0+zArray[startN-1];
		k1=k0+NS*xArray[bLength-1]*axX+EW*bLength*axY;
		if(DEBUG>1 && depth > 0) System.out.println("planWall "+IDString()+", depth="+depth+" n="+startN+" maxlLen="+maxLength+" at ("+i1+","+j1+","+k1+")");
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
				if(IS_LIQUID_BLOCK[getBlockIdLocal(x1,WalkHeight,0)])
					failCode=FAIL_UNDERWATER;
				if(!isWallable(x1,obstructionHeight,0) && failCode==NO_FAIL)
					failCode=FAIL_OBSTRUCTED;
				
			}

			gradz=(gradz+(bWidth+2)/2)/(bWidth+2)-SEARCHDOWN;
			if(failCode==FAIL_HIT_WALL) gradz=0;
			if(failCode==NO_FAIL && gradz < -1) failCode=FAIL_TOO_STEEP_DOWN;
			if(failCode==NO_FAIL && gradz > 4) failCode=FAIL_TOO_STEEP_UP;
			
			gradz=signum(gradz, 0);
			if(minJ!=NO_MIN_J && zArray[bLength-1]+gradz+j0 < minJ) gradz=0; //don't go below minJ
			if(gradz==0){
				int HorizForceThreshold=bWidth/2;
				int bias= target ? signum(xArray[bLength-1]-x_targ)*(2*HorizForceThreshold) : 0;
				gradx= (gradx > HorizForceThreshold + bias ? 1 : (gradx < -HorizForceThreshold + bias ? -1 : 0));
			} else gradx=0;
			

			shiftOrigin(gradx,gradz,1);
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


								branch=new BuildingWall(this, maxLength, i0, j0, k0);
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
	//
	//PARAMETERS: endTWidth - length of terminating tower, 0 if none
	//
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


		for(int n=0;n<bLength;n++){	
			setCursor(n);
			if(n==0) layer=shifted;
			else if(xArray[n-1]<xArray[n]) layer=shiftedRight;
			else if(xArray[n-1]>xArray[n]) layer=shiftedLeft;
			else if(zArray[n-1]<zArray[n]) layer=shiftedUp;
			else if(zArray[n-1]>zArray[n]) layer=shiftedDown;
			else if(n==bLength-1 || xArray[n+1]!=xArray[n] || zArray[n+1]!=zArray[n]) layer=shifted;
			else layer=ws.template[lN];

			if(layer==ws.template[lN]) lN=(lN+1) % ws.height;
			else lN=0;

			//wall
			for(int x1=0; x1<bWidth;x1++){
				
				//come down from top, clear everything below first non-wall block
				boolean keepWallFromAbove=true;
				for(int z1=bHeight+OVERHEAD_CLEARENCE-1; z1>=-ws.embed; z1--){
					boolean isWall=isWallBlock(x1,z1,0);
					idAndMeta= z1<bHeight ? ws.rules[layer[z1+ws.embed][x1]].getBlockOrHole(random) : TemplateRule.AIR_BLOCK;
					if(!(isWall && (idAndMeta[0]==0 || idAndMeta[0]==TemplateRule.HOLE_ID))) keepWallFromAbove=false;
					
					
					//TODO - do we still need this keepWallFromAbove stuff with new build order?
					if(!keepWallFromAbove){
						if(idAndMeta[0]!=WALL_STAIR_ID){
							if(z1<WalkHeight || !( x1==0 && (isWall || isWallBlock(-1,WalkHeight-1,0) || isWallBlock(-1,WalkHeight-2,0))) 
									&& !( x1==bWidth-1 && (isWall || isFloor(bWidth,WalkHeight-1,0) || isWallBlock(bWidth,WalkHeight-2,0))) ){  //don't clutter if merging walls
								if(idAndMeta[0]==TemplateRule.HOLE_ID) setBlockAndMetadataLocal(x1,z1,0,AIR_ID,0,true); //force lighting update for holes
								setBlockAndMetadataLocal(x1,z1,0,idAndMeta[0],idAndMeta[1]);  //straightforward build from template
						}}
						else if(!isWall){
							if(n>0 && zArray[n-1]>zArray[n]){  //stairs, going down
								if((n==1 || zArray[n-2]==zArray[n-1]) && (n==bLength-1 || zArray[n]==zArray[n+1]))
									setBlockAndMetadataLocal(x1, z1, 0, STEP_ID, idAndMeta[1]);
								else setBlockAndMetadataLocal(x1, z1, 0, STEP_TO_STAIRS[idAndMeta[1]],0);
							}
							else if(n<bLength-1 && zArray[n]<zArray[n+1]){ //stairs, going up
								if((n==0 || zArray[n-1]==zArray[n]) && (n==bLength-2 || zArray[n+1]==zArray[n+2]))
									setBlockAndMetadataLocal(x1, z1, 0, STEP_ID, idAndMeta[1]);
								else setBlockAndMetadataLocal(x1, z1, 0, STEP_TO_STAIRS[idAndMeta[1]],1);
							}
							else setBlockLocal(x1,z1,0,AIR_ID);
						}
					}
				}
			}
			//base
			for(int x1=0; x1<bWidth;x1++) 
				buildDown(x1,-1-ws.embed,0,ws.rules[base[x1]],ws.leveling,Math.min(2,ws.embed),3);


			clearTrees();
			mergeWallLayer();

			//DEBUGGING, creates signs with ID/distance info
			if(DEBUG_SIGNS && (n) % 10==0){
				//String[] lines=new String[]{IDString().split(" ")[0],IDString().split(" ")[1],"Dist:"+n+ " / "+planL,globalCoordString(1,WalkHeight,0)};
				String[] lines=new String[]{IDString().split(" ")[0],j0+"","Dist:"+n+ " / "+bLength,globalCoordString(1,WalkHeight,0)};
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
		//wall-merging stairs
		if(isFloor(-1,WalkHeight-1,0))   setBlockAndMetadataLocal(-1, WalkHeight-1, 0, STEP_ID, halfStairValue);
		if(isFloor(bWidth,WalkHeight-1,0))   setBlockAndMetadataLocal(bWidth, WalkHeight-1, 0, STEP_ID, halfStairValue);
		if(isFloor(-1,WalkHeight+1,0)  && isFloor(-2,WalkHeight+2,0) && isFloor(-2,WalkHeight+2,1)  && isFloor(-2,WalkHeight+2,-1)) 
			setBlockAndMetadataLocal(0, WalkHeight, 0, STEP_ID,halfStairValue);
		if(isFloor(bWidth,WalkHeight+1,0)  && isFloor(bWidth+1,WalkHeight+2,0) && isFloor(bWidth+1,WalkHeight+2,1)  && isFloor(bWidth+1,WalkHeight+2,-1)) 
			setBlockAndMetadataLocal(bWidth-1, WalkHeight, 0, STEP_ID, halfStairValue);

		if(getBlockIdLocal(-1,WalkHeight-1,0)==COBBLESTONE_STAIRS_ID || getBlockIdLocal(-1,WalkHeight-1,0)==WOOD_STAIRS_ID )
			setBlockAndMetadataLocal(-1, WalkHeight-1, 0, bRule.primaryBlock[0],bRule.primaryBlock[1]);
		if(getBlockIdLocal(bWidth,WalkHeight-1,0)==COBBLESTONE_STAIRS_ID || getBlockIdLocal(bWidth,WalkHeight-1,0)==WOOD_STAIRS_ID)
			setBlockAndMetadataLocal(bWidth, WalkHeight-1, 0, bRule.primaryBlock[0],bRule.primaryBlock[1]);
	}


	//****************************************  FUNCTION - buildTowers *************************************************************************************//

	public void buildTowers(boolean lSideTowers,boolean rSideTowers, boolean gatehouseTowers, boolean overlapTowers, boolean isAvenue) throws InterruptedException{
		if(ws==null){
			System.out.println("Tried to build towers but wall style was null!");
			return;
		}
		if(!ws.MakeBuildings) return;

		int n=Math.max(ws.getTMaxWidth(circular)+3,2*ws.BuildingInterval/3);
		//boolean enteredPreGateway=false, enteredPostGateway=false, passedGateway=false;
		while(n<bLength){
			setCursor(n);
			int tw=ws.pickTWidth(circular,random);
			int th=ws.pickTHeight(circular,random);
			int tl=circular ? tw : ws.pickTWidth(circular,random);
			int twrNMid=n-tw/2-2;
			int twrDXMid=xArray[twrNMid] - xArray[n], twrDZMid=zArray[twrNMid] - zArray[n];
			int clearSide=-bHand*signum(curvature(xArray[n-tw-3], xArray[twrNMid], xArray[n], 0),0);
			if(clearSide==0){
				if(lSideTowers && rSideTowers) clearSide=2*random.nextInt(2)-1;
				else clearSide= lSideTowers ? L_HAND : R_HAND;
			}
			
			//towers are built from n-2 to n-tw-1
			if(gatewayStart!=NO_GATEWAY && n>=gatewayStart && n<=gatewayEnd+ws.getTMaxWidth(circular)+3){ 
				//don't built if there's a gateway
				n=gatewayEnd+ws.getTMaxWidth(circular)+3;
				if(n>=bLength) break;
			}
			
			//try tower types
			boolean built=false;
			if(gatehouseTowers && ws.DefaultTowerWeight>0 && !circular && curvature(zArray[n-tw-3], zArray[twrNMid], zArray[n], 0)==0 
															   && curvature(xArray[n-tw-3], xArray[twrNMid], xArray[n], 2)==0){
				
				if(DEBUG>1) System.out.println("Building gatehouse for "+IDString()+" at n="+n+" "+globalCoordString(0,0,0)+" width "+tw);
				BuildingTower tower = new BuildingTower(bID+n, this, -bDir, -bHand, tw, th, tl, getIJKPt(twrDXMid+bWidth/2-tw/2,twrDZMid,-2));
				if(!tower.isObstructedRoof()){
					wgt.setLayoutCode(tower.getIJKPt(0,0,0),tower.getIJKPt(tw-1,0,tw-1), WorldGeneratorThread.LAYOUT_CODE_TOWER);
					tower.build(xArray[n-1]-xArray[twrNMid], xArray[n-tw-2]-xArray[twrNMid], false);
					
					n+=ws.BuildingInterval;
					built=true;
				}
			}
			else if((lSideTowers && clearSide==L_HAND) || (rSideTowers && clearSide==R_HAND)) {   //side towers
				if(DEBUG>1) System.out.println("Building side tower for "+IDString()+" at n="+n+" "+globalCoordString(0,0,0)+" with clearSide="+clearSide+" width "+tw);
				TemplateTML building=ws.buildings.get(Building.selectWeightedOption(random,ws.buildingWeights[0],ws.buildingWeights[1]));
				int y1=(building==WallStyle.DEFAULT_TOWER ? -2 : (building.length-tw)/2 - 2);

				if(building==WallStyle.DEFAULT_TOWER){
					int ybuffer=(isAvenue ? 0:1) - ws.TowerXOffset;
					int x1=twrDXMid+(clearSide==bHand ? (bWidth - ybuffer):ybuffer-1);
					BuildingTower tower=new BuildingTower(bID+n,this, rotateDir(bDir,clearSide), clearSide, tw, th, tl, getIJKPt(x1,twrDZMid,y1));
					if(tower.queryCanBuild(ybuffer,overlapTowers)){
						tower.build(0,0,true);
						//new BuildingCone(0,wgt,Building.OBSIDIAN_ID,rotateDir(dir,clearSide), clearSide, tw,getIJKPt(x1,twrDZMid,y1)).build();
						n+=ws.BuildingInterval;
						built=true;
					}
				}
				else{
					int x1=twrDXMid+(clearSide==bHand ? bWidth:-1);
					BuildingTML buildingTML=new BuildingTML(bID+n,wgt,rotateDir(bDir,clearSide),clearSide,building,getIJKPt(x1,twrDZMid,y1));
					if(buildingTML.queryCanBuild(0)){
						buildingTML.build();
						n+=ws.BuildingInterval;
						built=true;
					}
				}
			}
			
			if(!built) n++;
		}

		//build towers at endpoints
		
		if(endTLength >= BuildingTower.TOWER_UNIV_MIN_WIDTH){
			if(DEBUG>1) System.out.println("Building end tower for "+IDString()+" at n="+bLength+" "+globalCoordString(0,0,0));
			int tl=endTLength;
			int tw=circular ? tl : ws.pickTWidth(circular,random);
			while(tl >= ws.getTMinWidth(circular)){
				int endTX=( bLength>1 ? xArray[bLength-2] : (bLength<=0 ? 0:xArray[0]))+bWidth/2-tw/2;
				int endTZ=bLength>1 ? zArray[bLength-2] : (bLength<=0 ? 0:zArray[0]);
				int endTY=bLength>1 ? bLength-1 : 0;
				BuildingTower tower=new BuildingTower(bID+bLength,this,bDir,bHand,tw, ws.pickTHeight(circular,random), tl,
										new int[]{i0+EW*axX*endTX+NS*axY*endTY,j0+endTZ,k0+NS*axX*endTX+EW*axY*endTY});
				if(tower.queryCanBuild(1,overlapTowers)){
					tower.build(0,0,true);
					break;
				}
				tl--;
			}
		}
		setCursor(0);
	}







	//****************************  FUNCTION  - buildGateway  *************************************************************************************//
	//Builds a gateway and road on one side of gateway. Call after build() and before buildTowers().
	//
	//PARAMETERS:
	//startScan,endScan - bounds of where to look to place gateway
	//gateHeight, gateWidth - dimensions of the gateway in the wall
	//exitPath - if true, try to build a road and only accept gateway if sucessful
	//minExitPathLength - minimum length of road to build
	//RETURNS:
	//y-position where gateway was build or -1 if no gateways was built
	//
	//public int buildGateway(int startScan, int endScan, int gateHeight,int gateWidth,boolean exitPath, int minExitPathLength){
		
	public BuildingWall[] buildGateway(int startScan, int endScan, int gateHeight,int gateWidth,WallStyle rs,int flankTHand,
			int XMaxLen,int[] XTarget,int XHand, int antiXMaxLen, int[] antiXTarget, int antiXHand) throws InterruptedException {
		BuildingWall[] avenues=null;
		if(rs!=null) gateWidth=rs.WWidth;
		
		for(int m=0; Math.abs(m)<(endScan-startScan)/2; m=-m+(m<=0 ? 1:0)){
			int n=(endScan + startScan)/2+m;
			if(curvature(zArray[n], zArray[n-gateWidth/2], zArray[n-gateWidth-1], 1)==0 &&
			   curvature(xArray[n], xArray[n-gateWidth/2], xArray[n-gateWidth-1], 0)==0)
			{
				setCursor(n-gateWidth);
				int tw=ws.pickTWidth(circular,random), th=ws.getTMaxHeight(circular);
				if(rs!=null){
					avenues=new BuildingWall[]{ new BuildingWall(bID,wgt,rs,rotateDir(bDir,bHand),XHand, XMaxLen,false,getIJKPt(bWidth,0,XHand==-bHand ? 0 :gateWidth-1))
					         ,new BuildingWall(bID,wgt,rs,rotateDir(bDir,-bHand),antiXHand, antiXMaxLen,false,getIJKPt(-1,0,antiXHand==bHand ? 0 :gateWidth-1))};

					avenues[0].setTarget(XTarget==null ? getIJKPt(bWidth+tw,0,XHand==-bHand ? 0 :gateWidth-1) : XTarget);
					avenues[0].plan(1,0,DEFAULT_LOOKAHEAD,true);
					if(XTarget==null && avenues[0].bLength>=tw){
						avenues[0].target=false;
						avenues[0].plan(tw+1,0,DEFAULT_LOOKAHEAD,true);
					}
			
					if(avenues[0].bLength>=MIN_GATEWAY_ROAD_LENGTH){
						avenues[1].setTarget(antiXTarget==null ? getIJKPt(-1-tw,0,antiXHand==bHand ? 0 :gateWidth-1) : antiXTarget);
						avenues[1].plan(1,0,DEFAULT_LOOKAHEAD,true);
						if(antiXTarget==null && avenues[1].bLength>=tw){
							avenues[1].target=false;
							avenues[1].plan(tw+1,0,DEFAULT_LOOKAHEAD,true);
						}
					}
				}
				
				//build it
				if(rs==null ||  avenues[1].bLength>=MIN_GATEWAY_ROAD_LENGTH){ 
					if(rs!=null){
						avenues[0].smooth(10,10,false);
						avenues[1].smooth(10,10,false);
					}
					gateHeight=Math.min(gateHeight,bHeight-1);
					for(int x1=0;x1<bWidth;x1++)
						for(int y1=0;y1<gateWidth;y1++)
							for(int z1=0;z1<gateHeight;z1++)
								if(!((y1==0 || y1==gateWidth-1) && z1==gateHeight-1))
									setBlockLocal(x1,z1,y1,AIR_ID);
					if(flankTHand!=-bHand ) setBlockAndMetadataLocal(-1,gateHeight-2,-1,TORCH_ID,3);
					if(flankTHand!=-bHand ) setBlockAndMetadataLocal(-1,gateHeight-2,gateWidth,TORCH_ID,3);
					if(flankTHand!=bHand ) setBlockAndMetadataLocal(bWidth,gateHeight-2,-1,TORCH_ID,4);
					if(flankTHand!=bHand ) setBlockAndMetadataLocal(bWidth,gateHeight-2,gateWidth,TORCH_ID,4);
					
					//build flanking towers
					if(n+gateWidth+tw > bLength) flankTHand=0;
					if(flankTHand!=0){
						int tnMid1=n-tw/2;
						int dx1=xArray[tnMid1]-xArray[n];
						int tx1=(flankTHand==bHand ? (bWidth-1+(dx1<0 ? dx1:0)+ws.TowerXOffset) : (-ws.TowerXOffset + dx1>0 ? dx1:0));
						BuildingTower tower1=new BuildingTower(0,this, rotateDir(bDir,flankTHand), bHand, tw, th, tw,
																getIJKPt(tx1,zArray[tnMid1]-zArray[n],-1));
						tower1.build(0,0,false);
						
						int tnMid2=n+gateWidth+tw/2;
						int dx2=xArray[tnMid2]-xArray[n];
						int tx2=(flankTHand==bHand ? (bWidth-1+(dx2<0 ? dx2:0)+ws.TowerXOffset) : (-ws.TowerXOffset + dx2>0 ? dx2:0));
						BuildingTower tower2=new BuildingTower(0,this, rotateDir(bDir,flankTHand),-bHand, tw, th+zArray[tnMid1]-zArray[tnMid2], tw,
																getIJKPt(tx2, zArray[tnMid2]-zArray[n], gateWidth));
						tower2.build(0,0,false);
						
					}
					flushDelayed();
					
					//lighting
					/*
					for(int x1=0;x1<WWidth;x1++){
						int[] pt=getIJKPt(x1,0,gateWidth/2);
						wgt.explorationHandler.queueLighting(new int[]{pt[0],pt[1],pt[2],pt[0],pt[1],pt[2]});
					}
					*/
					
					
					gatewayStart=n-(flankTHand!=0 ? tw+ws.BuildingInterval/2:0);
					gatewayEnd=n+gateWidth-1+(flankTHand!=0 ? (tw+ws.BuildingInterval/2):0);
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

