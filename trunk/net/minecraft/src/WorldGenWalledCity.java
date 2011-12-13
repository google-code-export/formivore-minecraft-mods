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
 * WorldGenWalledCity generates walled cities in the Minecraft world.
 * Walled cities are composed of 4 wall template BuildingWalls in a rough rectangle,
 *  filled with many street template BuildingDoubleWalls.
 */

import java.util.Random;
import java.util.LinkedList;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.Math;

public class WorldGenWalledCity extends WorldGeneratorThread
{
	private final static int GATE_HEIGHT=6;
	private final static int JMEAN_DEVIATION_SLOPE=10;
	private final static int LEVELLING_DEVIATION_SLOPE=18;
	private final static int MIN_SIDE_LENGTH=10; //can be less than MIN_CITY_LENGTH due to squiggles
	private final static float MAX_WATER_PERCENTAGE=0.4f;

	//private final static int[] DIR_GROUP_TO_DIR_CODE=new int[]{Building.DIR_NORTH,Building.DIR_EAST,Building.DIR_SOUTH,Building.DIR_WEST};

	//**** WORKING VARIABLES **** 
	private mod_WalledCity wc;
	private TemplateWall ows, sws;
	private BuildingWall[] walls;
	private int axXHand;
	private int[] dir=null;
	private int Lmean, jmean;
	private int cityType;
	private int corner1[], corner2[], mincorner[];
	public int[][] layout;

	//****************************************  CONSTRUCTOR - WorldGenWalledCity  *************************************************************************************//
	public WorldGenWalledCity (mod_WalledCity wc_,World world_, Random random_, int chunkI_, int chunkK_, int TriesPerChunk_, double ChunkTryProb_) { 
		super(wc_, world_, random_, chunkI_, chunkK_, TriesPerChunk_, ChunkTryProb_);
		wc=wc_;
		ConcaveSmoothingScale=wc.ConcaveSmoothingScale;
		ConvexSmoothingScale=wc.ConcaveSmoothingScale;
		BacktrackLength=wc.BacktrackLength;
		chestTries=wc.chestTries;
		chestItems=wc.chestItems;
		cityType=Building.isNether(world) ? mod_WalledCity.CITY_TYPE_NETHER : mod_WalledCity.CITY_TYPE_SURFACE;
		setName("WorldGenWalledCityThread");
		
	}
	
	//****************************************  FUNCTION - generate  *************************************************************************************//
	@Override
	public boolean generate(int i0,int j0,int k0) throws InterruptedException{
		ows=TemplateWall.pickBiomeWeightedWallStyle(wc.cityStyles,world,i0,k0,random,false);
		if(ows==null) return false;
		sws=TemplateWall.pickBiomeWeightedWallStyle(ows.streets,world,i0,k0,random,false);
		if(sws==null) return false;
		if(!wc.cityIsSeparated(i0,k0,cityType)) return false;
		
		int ID=(random.nextInt(9000)+1000)*100;
		int minJ=ows.LevelInterior ? world.field_35472_c/2 - 2 : BuildingWall.NO_MIN_J;
		//boolean circular=random.nextFloat() < ows.CircularProb;
		chooseDirection(i0 >> 4, k0 >>4);


		//==========================      outer walls    ====================================================
		if(ows.MinL < mod_WalledCity.MIN_CITY_LENGTH) ows.MinL=mod_WalledCity.MIN_CITY_LENGTH;
		walls=new BuildingWall[4];
		ows.setFixedRules(random);

		//plan walls[0]
		walls[0] = new BuildingWall(ID,this,ows,dir[0],axXHand,ows.MinL+random.nextInt(ows.MaxL-ows.MinL),false,i0,j0,k0).setMinJ(minJ);
		walls[0].plan(1,0,BuildingWall.DEFAULT_LOOKAHEAD,true);
		if(BuildingWall.DEBUG>1) System.out.println("Planning for "+walls[0].IDString()+" from "+walls[0].globalCoordString(0,0,0));
		if(walls[0].bLength<ows.MinL) return false;

		//plan walls[1]
		walls[0].setCursor(walls[0].bLength-1);
		walls[1] = new BuildingWall(ID+1,this,ows,dir[1],axXHand, ows.MinL+random.nextInt(ows.MaxL-ows.MinL),false,walls[0].getIJKPt(-1-ows.TowerXOffset,0,1+ows.TowerXOffset)).setTowers(walls[0]).setMinJ(minJ);
		if(!wc.cityIsSeparated(walls[1].i1,walls[1].k1,cityType)) return false;
		walls[1].plan(1,0,BuildingWall.DEFAULT_LOOKAHEAD,false);
		if(BuildingWall.DEBUG>1) System.out.println("Planning for "+walls[1].IDString()+" from "+walls[1].globalCoordString(0,0,0));
		if(walls[1].bLength<ows.MinL) return false;

		//plan walls[2]
		walls[1].setCursor(walls[1].bLength-1);
		int distToTarget=walls[0].bLength + walls[1].xArray[walls[1].bLength-1];
		if(distToTarget<MIN_SIDE_LENGTH) return false;
		walls[2] = new BuildingWall(ID+2,this,ows,dir[2],axXHand,distToTarget+2,false,walls[1].getIJKPt(-1-ows.TowerXOffset,0,1+ows.TowerXOffset)).setTowers(walls[0]).setMinJ(minJ);
		if(!wc.cityIsSeparated(walls[2].i1,walls[2].k1,cityType)) return false;
		walls[2].setCursor(0);
		walls[2].setTarget(walls[2].getIJKPt(0,0,distToTarget));
		walls[2].plan(1,0,BuildingWall.DEFAULT_LOOKAHEAD,false);
		if(BuildingWall.DEBUG>1) System.out.println("Planning for "+walls[2].IDString()+" from "+walls[2].globalCoordString(0,0,0));
		if(walls[2].bLength<walls[2].y_targ){
			if(BuildingWall.DEBUG>1) System.out.println("Abandoning on 3rd wall "+walls[2].IDString()+" planned length "+walls[2].bLength+" less than targeted length "+walls[2].y_targ+". Reason: "+walls[2].failString());
			return false;
		}

		//plan walls[3]
		walls[2].setCursor(walls[2].bLength-1);
		distToTarget=walls[1].bLength - walls[0].xArray[walls[0].bLength-1] + walls[1].xArray[walls[1].bLength-1];
		if(distToTarget<MIN_SIDE_LENGTH) return false;
		walls[3] = new BuildingWall(ID+3,this,ows,dir[3],axXHand,distToTarget+2,false,walls[2].getIJKPt(-1-ows.TowerXOffset,0,1+ows.TowerXOffset)).setTowers(walls[0]).setMinJ(minJ);
		if(!wc.cityIsSeparated(walls[3].i1,walls[3].k1,cityType)) return false;
		walls[0].setCursor(0);
		walls[3].setCursor(0);
		walls[3].setTarget(walls[0].getIJKPt(-1-ows.TowerXOffset,0,-1-ows.TowerXOffset));
		if(BuildingWall.DEBUG>1) System.out.println("Planning for "+walls[3].IDString()+" from "+walls[3].globalCoordString(0,0,0));
		walls[3].plan(1,0,BuildingWall.DEFAULT_LOOKAHEAD,false);
		if(walls[3].bLength<walls[3].y_targ){
			if(BuildingWall.DEBUG>1)  System.out.println("Abandoning on 4th wall "+walls[3].IDString()+" planned length "+walls[3].bLength+" less than targeted "+walls[3].y_targ+". Reason: "+walls[3].failString());
			return false;
		}
		
		//smoothing
		for(BuildingWall w : walls) w.smooth(wc.ConcaveSmoothingScale,wc.ConcaveSmoothingScale,true);
		
		//======================= Additional site checks =======================================
		
		//calculate the corners
		int[] xmax=new int[4];
		for(int w=0;w<4;w++){
			xmax[w]=0;
			for(int n=0; n<walls[w].bLength; n++)
				if(walls[w].xArray[n]>xmax[w]) xmax[w]=walls[w].xArray[n];
		}
		for(BuildingWall w : walls) w.setCursor(0);
		corner1=walls[1].getIJKPt(xmax[1]+walls[1].bWidth+1, 0, walls[0].xArray[walls[0].bLength-1] - xmax[0] - walls[0].bWidth - 2);
		corner2=walls[3].getIJKPt(xmax[3]+walls[3].bWidth+1, 0, walls[2].xArray[walls[2].bLength-1] - xmax[2] - walls[2].bWidth - 2);
		mincorner=new int[]{Math.min(corner1[0], corner2[0]),0,Math.min(corner1[2], corner2[2])};
		
		//reject cities if too z-displaced at corners
		Lmean=(walls[0].bLength + walls[1].bLength + walls[2].bLength + walls[3].bLength)/4;
		jmean=0;
		for(BuildingWall w : walls) for(int n=0;n<w.bLength;n++) jmean+=w.zArray[n]+w.j1;
		jmean/=(Lmean*4);
		for(BuildingWall w : walls){
			if(Math.abs(w.j1 - jmean) > w.bLength/JMEAN_DEVIATION_SLOPE) {
				wc.logOrPrint("Rejected city "+ID+", height at corner differed from mean by "+(Math.abs(w.j1 - jmean))+".");
				return false;
			}
		}
		
		int cityArea=0,waterArea=0;
		int incI=Building.signum(corner2[0]-corner1[0],0), incK=Building.signum(corner2[2]-corner1[2],0);
		for(int i2=corner1[0]; (corner2[0]-i2)*incI > 0; i2+=incI){
			for(int k2=corner1[2]; (corner2[2]-k2)*incK > 0; k2+=incK){
				boolean enclosed=true;
				for(BuildingWall w : walls) if(w.ptIsToXHand(new int[]{i2,0,k2},1)) enclosed=false;
				if(enclosed){
					int j2=Building.findSurfaceJ(world,i2,k2,world.field_35472_c-1,true,3);
					cityArea++;
					if(j2==Building.HIT_WATER) waterArea++;
					if(Building.IS_ARTIFICAL_BLOCK[world.getBlockId(i2,j2,k2)]){
						wc.logOrPrint("Rejected "+ows.name+" city "+ID+", found previous construction in city zone!");
						//return false;
					}
				}
		}}
		if(!ows.LevelInterior && (float)waterArea/(float)cityArea > MAX_WATER_PERCENTAGE){
			wc.logOrPrint("Rejected "+ows.name+" city "+ID+", too much water! City area was " +(100.0f*(float)waterArea/(float)cityArea)+"% water!");
			//return false;
		}
		
		
		
		//query the exploration handler again to see if we've built nearby cities in the meanwhile
		for(BuildingWall w : walls){
			if(!wc.cityIsSeparated(w.i1,w.k1,cityType)){
				wc.logOrPrint("Rejected city "+ID+" nearby city was built during planning!");
				return false;
			}
		}
		//We've passed all checks, register this city site
		walls[0].setCursor(0);
		int[] cityCenter=walls[0].getSurfaceIJKPt(-walls[1].bLength/2, walls[0].bLength/2,world.field_35472_c,false,Building.IGNORE_WATER);
		for(int w=0;w<4;w++) 
			wc.cityLocations.add(new int[]{walls[w].i1,walls[w].k1,cityType});
		wc.saveCityLocations();


		//=================================== Build it! =========================================
		exploreArea(corner1, corner2, true);
		willBuild=true;
		if(!master.isFlushingGenThreads) suspendGen();
		
		wc.chatBuildingCity("\n***** Building "+ows.name+" city"+", ID="+ID+" in "+Building.BIOME_NAMES[Building.getBiomeNum(world.getWorldChunkManager().getBiomeGenAt(walls[0].i1,walls[0].k1))]+" biome between "+walls[0].globalCoordString(0,0,0)+" and "+walls[2].globalCoordString(0,0,0) + " ******\n");
		if(ows.LevelInterior) levelCity();
		
		TemplateWall avenueWS=TemplateWall.pickBiomeWeightedWallStyle(ows.streets,world,i0,k0,random,false);
		LinkedList<BuildingWall> interiorAvenues=new LinkedList<BuildingWall>();
		
		//layout
		layout=new int[Math.abs(corner1[0]-corner2[0])][Math.abs(corner1[2]-corner2[2])];
		for(int x=0;x<layout.length;x++) for(int y=0;y<layout[0].length;y++) layout[x][y]=LAYOUT_CODE_EMPTY;
		for(BuildingWall w : walls) w.setLayoutCode(LAYOUT_CODE_WALL);

		int gateFlankingTowers=0;
		for(BuildingWall w : walls){
			//build city walls
			if(BuildingWall.DEBUG > 1) w.printWall();
			w.endBLength=0;
			w.buildFromTML();
			BuildingWall[] avenues=w.buildGateway(w.bLength/4,3*w.bLength/4,GATE_HEIGHT,avenueWS.WWidth,avenueWS,random.nextInt(6)<gateFlankingTowers ? 0:axXHand,500,null,-axXHand,150,cityCenter,w.bDir>1 ? 1:-1);
			w.makeBuildings(axXHand==-1,axXHand==1,true,false, false);
			if(w.gatewayStart!=BuildingWall.NO_GATEWAY) gateFlankingTowers++;

			
			//build avenues
			if(avenues!=null){
				avenues[0].buildFromTML();
				//avenues[1].buildFromTML(true);
				interiorAvenues.add(avenues[1]);
			}else {
				//no gateway on this city side, try just building an interior avenue from midpoint
				w.setCursor(w.bLength/2);
				BuildingWall midpointAvenue=new BuildingWall(0, this,sws,Building.rotDir(w.bDir,-axXHand),w.bDir>1 ? 1:-1, ows.MaxL,false,w.getSurfaceIJKPt(-1, 0,world.field_35472_c,false,Building.IGNORE_WATER));
				midpointAvenue.setTarget(cityCenter);
				midpointAvenue.plan(1,0,BuildingWall.DEFAULT_LOOKAHEAD,true);
				if(midpointAvenue.bLength > 20){
					if(BuildingWall.DEBUG > 1) System.out.println("Built a non-gateway avenue for dir="+w.bDir+" wall.");
					midpointAvenue.smooth(10,10,true);
					//midpointAvenue.buildFromTML(true);
					interiorAvenues.add(midpointAvenue);
				}
			}
		}
		
		

		//corner towers
		for(BuildingWall w : walls) w.setCursor(0);
		if(ows.MakeEndTowers){ //allow MakeEndTowers to control corner towers so we can have an "invisible wall"...
			for(int w=0;w<4;w++){
				int zmean=(walls[w].zArray[2]-walls[w].j1+walls[(w+3)%4].zArray[walls[(w+3)%4].bLength-3]+walls[(w+3)%4].j1) / 2;
				int minCornerWidth=ows.WWidth+2+(ows.TowerXOffset < 0 ? 2*ows.TowerXOffset:0);
				int TWidth= ows.getTMaxWidth(walls[w].circular) < minCornerWidth ? minCornerWidth : ows.getTMaxWidth(walls[w].circular) ;
				BuildingTower tower=new BuildingTower(ID+10+w,walls[w], dir[(w+2)%4], -axXHand, false, TWidth, ows.getTMaxHeight(walls[w].circular), TWidth,
						                              walls[w].getIJKPt(-2-(ows.TowerXOffset < 0 ? ows.TowerXOffset:0),zmean,2));
				setLayoutCode(tower.getIJKPt(0,0,0),tower.getIJKPt(TWidth-1,0,TWidth-1),LAYOUT_CODE_TOWER);
				tower.build(0,0,true);
			}
		}
		
		

		
		
		if(!master.isFlushingGenThreads) suspendGen();
		//===============================================      streets   ===============================================
		//build avenues and cross avenues
		LinkedList<BuildingDoubleWall> branchAvenues=new LinkedList<BuildingDoubleWall>();
		int avInterval=ows.StreetDensity > 3*TemplateWall.MAX_STREET_DENSITY/4 ? 60 : 35;
		for(BuildingWall avenue : interiorAvenues){
			for(int n=avenue.bLength-avInterval; n>=25; n-=avInterval){
				avenue.setCursor(n);
				BuildingDoubleWall crossAvenue=new BuildingDoubleWall(ID,this,sws,Building.rotDir(avenue.bDir,Building.ROT_R),Building.R_HAND,avenue.getIJKPt(0,0,0));
				if(crossAvenue.plan())
					branchAvenues.add(crossAvenue);
			}
			avenue.buildFromTML();
			avenue.setLayoutCode(LAYOUT_CODE_AVENUE);
		}
		for(BuildingDoubleWall avenue : branchAvenues) avenue.build(LAYOUT_CODE_AVENUE);
		

		//number of streets scales as Lmean^(3/2). Filling a 2-D space with 1-D objects, but these objects don't go on forever.
		int maxTries=Lmean*((int)Math.sqrt((double)Lmean))*ows.StreetDensity*ows.StreetDensity/90;
		
		LinkedList<BuildingDoubleWall> plannedStreets=new LinkedList<BuildingDoubleWall>();

		for(int tries=0;tries<maxTries; tries++){
			
			if(tries % 5==0 && !master.isFlushingGenThreads) suspendGen();
			int[] pt=randInteriorPoint();
			pt[1]++;//want block above surface block
			sws=TemplateWall.pickBiomeWeightedWallStyle(ows.streets,world,i0,k0,random,true);
			if(pt!=null && pt[1]!=-1){

				//streets
				BuildingDoubleWall street=new BuildingDoubleWall(ID+tries,this,sws,random.nextInt(4),Building.R_HAND,pt);
				if(street.plan()) plannedStreets.add(street);
			}
		}
		for(BuildingDoubleWall street : plannedStreets) street.build(LAYOUT_CODE_STREET);
		
		//build towers
		boolean overlapTowers=ows.StreetDensity >= TemplateWall.MAX_STREET_DENSITY/2;
		for(BuildingWall avenue : interiorAvenues)
			avenue.makeBuildings(true,true,false,overlapTowers, true);
		for(BuildingDoubleWall avenue : branchAvenues)
			avenue.buildTowers(true,true,false,overlapTowers, true);
		for(BuildingDoubleWall street : plannedStreets){
			if(!master.isFlushingGenThreads) suspendGen();
			street.buildTowers(true,true,sws.MakeGatehouseTowers,overlapTowers, false);
		}
		
		
		wc.chatCityBuilt(new int[]{i0,j0,k0,cityType,Lmean/2+40});
		
		//printLayout(new File("layout.txt"));
		
		//guard against memory leaks
		layout=null; 
		walls=null;
		
		return true;
	}
	

	
	
	//****************************************  FUNCTION - layoutIsClear *************************************************************************************//
	@Override
	public boolean isLayoutGenerator(){ return true; }
	
	@Override
	public boolean layoutIsClear(int[] pt1, int[] pt2, int layoutCode){
		for(int i=Math.min(pt1[0],pt2[0]); i<=Math.max(pt1[0],pt2[0]); i++)
			for(int k=Math.min(pt1[2],pt2[2]); k<=Math.max(pt1[2],pt2[2]); k++)
				if(i>=mincorner[0] && k>=mincorner[2] && i-mincorner[0]<layout.length && k-mincorner[2] < layout[0].length)
					if(LAYOUT_CODE_OVERRIDE_MATRIX[layout[i-mincorner[0]][k-mincorner[2]]][layoutCode]==0)
						return false;
		return true;
	}
	
	@Override
	public boolean layoutIsClear(Building building, boolean[][] templateLayout, int layoutCode){
		for(int y=0; y<templateLayout.length;y++){
    		for(int x=0; x<templateLayout[0].length;x++){
    			if(templateLayout[y][x]){
    				int i=building.getI(x,y), k=building.getK(x, y);
    				if(i>=mincorner[0] && k>=mincorner[2] && i-mincorner[0]<layout.length && k-mincorner[2] < layout[0].length)
    					if(LAYOUT_CODE_OVERRIDE_MATRIX[layout[i-mincorner[0]][k-mincorner[2]]][layoutCode]==0)
    						return false;
    	}}}
		return true;
	}
	
	//****************************************  FUNCTION - setLayoutCode *************************************************************************************//
	@Override
	public void setLayoutCode(int[] pt1, int[] pt2, int layoutCode){
		for(int i=Math.min(pt1[0],pt2[0]); i<=Math.max(pt1[0],pt2[0]); i++)
			for(int k=Math.min(pt1[2],pt2[2]); k<=Math.max(pt1[2],pt2[2]); k++)
				if(i>=mincorner[0] && k>=mincorner[2] && i-mincorner[0]<layout.length && k-mincorner[2] < layout[0].length)
					layout[i-mincorner[0]][k-mincorner[2]]=layoutCode;
	}
	
	@Override
	public void setLayoutCode(Building building, boolean[][] templateLayout, int layoutCode) {
		for(int y=0; y<templateLayout.length;y++){
    		for(int x=0; x<templateLayout[0].length;x++){
    			if(templateLayout[y][x]){
    				int i=building.getI(x,y), k=building.getK(x, y);
    				if(i>=mincorner[0] && k>=mincorner[2] && i-mincorner[0]<layout.length && k-mincorner[2] < layout[0].length)
    					layout[i-mincorner[0]][k-mincorner[2]]=layoutCode;
    	}}}	
	}
	
	//****************************************  FUNCTION - printLayout *************************************************************************************//
	private void printLayout(File f){
		try{
		 PrintWriter pw=new PrintWriter( new BufferedWriter( new FileWriter(f ) ));
		 pw.println("  +y   ");
		 pw.println("   ^   ");
		 pw.println("+x<.>-x");
		 pw.println("   v   ");
		 pw.println("  -y   ");
		 pw.println();
		 for(int y=layout[0].length-1;y>=0; y--){
			 for(int x=layout.length-1;x>=0;x--){
				 pw.print(LAYOUT_CODE_TO_CHAR[layout[x][y]]);
			 }
			 pw.println();
		 }
		 pw.close();
		}catch(Exception e){}
	}

	//****************************************  FUNCTION - randInteriorPoint  *************************************************************************************//
	//RETURNS: Coordinates (i,j,k) of interior surface point, j will be -1 if point was water
	private int[] randInteriorPoint(){
		int tries=0;
		int[] pt=new int[3];
		if(BuildingWall.DEBUG>1) System.out.println("Finding random interior point for city seeded at corner ("+walls[0].i1+","+walls[0].j1+","+walls[0].k1+")"+walls[0].IDString());
		while(tries < 20){
			pt[0]=mincorner[0] + random.nextInt( Math.abs(corner1[0]-corner2[0]));
			pt[2]=mincorner[2] + random.nextInt(Math.abs(corner1[2]-corner2[2]));
			pt[1]=Building.findSurfaceJ(world,pt[0],pt[2],world.field_35472_c-1,true,3);
			boolean enclosed=true;
			for(BuildingWall w : walls) if(w.ptIsToXHand(pt,-sws.WWidth)) enclosed=false;
			if(enclosed) return pt;
			tries++;
		}
		System.out.println("Could not find point within bounds!");
		return null;
	}

	

	//****************************************  FUNCTION - levelCity  *************************************************************************************//
	//BUKKIT PORT
	//replace world.getBlockId( with world.getBlockTypeIdAt(
	//replace world.setBlock( with world.getBlockAt().setTypeId(
	//replace world.setBlockAndMetadata( with world.getBlockAt().setTypeIdAndData(
	
	private void levelCity() throws InterruptedException{
		for(BuildingWall w : walls) w.setCursor(0);
		int incI=Building.signum(corner2[0]-corner1[0],0), incK=Building.signum(corner2[2]-corner1[2],0);
		int[] pt=new int[3];
		int jmin=Building.isNether(world) ? jmean : Math.max(jmean, world.field_35472_c/2);
		for(BuildingWall w : walls){
			for(int n=0;n<w.bLength;n++)
				if(w.zArray[n]+w.j1+w.WalkHeight-1 < jmin && (Building.isNether(world) || jmin >= world.field_35472_c/2))
					jmin=w.zArray[n]+w.j1+w.WalkHeight-1;
		}
		int jmax=Math.max(jmean + Lmean/LEVELLING_DEVIATION_SLOPE, jmin);
		//int jmax=Math.max(jmean + walls[0].WalkHeight, jmin);
		
		for(pt[0]=corner1[0]; (corner2[0]-pt[0])*incI > 0; pt[0]+=incI){
			for(pt[2]=corner1[2]; (corner2[2]-pt[2])*incK > 0; pt[2]+=incK){
				boolean enclosed=true;
				for(BuildingWall w : walls) if(w.ptIsToXHand(pt,1)) enclosed=false;
				if(enclosed){
					pt[1]=Building.findSurfaceJ(world,pt[0],pt[2],world.field_35472_c-1,false,Building.IGNORE_WATER);
					int oldSurfaceBlockId=world.getBlockId(pt[0], pt[1], pt[2]);
					if(pt[1]>jmax) {
						while(world.getBlockId(pt[0],pt[1]+1,pt[2])!=Building.AIR_ID) pt[1]++; //go back up to grab any trees or whatnot
						pt[1]+=10; //just to try to catch any overhanging blocks
						for(; pt[1]>jmax; pt[1]--)
							if(world.getBlockId(pt[0],pt[1],pt[2])!=Building.AIR_ID)
								Building.setBlockNoLighting(world,pt[0],pt[1],pt[2], Building.AIR_ID);
						if(world.getBlockId(pt[0],jmax-1,pt[2])!=Building.AIR_ID) 
							Building.setBlockNoLighting(world,pt[0],jmax,pt[2],oldSurfaceBlockId);
					}
					
					if(pt[1]<jmin) Building.fillDown(pt, jmin, world);
		}}}
		
		//update heightmap
		//BUKKIT PORT - comment out below block
		//MP PORT - replace generateheightMap(); with generateSkylightMap();
		for(int chunkI=corner1[0]>>4; ((corner2[0]>>4)-chunkI)*incI > 0; chunkI+=incI)
			for(int chunkK=corner1[2]>>4; ((corner2[2]>>4)-chunkK)*incK > 0; chunkK+=incK)
				world.getChunkFromChunkCoords(chunkI,chunkK).generateHeightMap();
		
		if(!master.isFlushingGenThreads) suspendGen();
	}
	
	//****************************************  FUNCTION - chooseDirection *************************************************************************************//
	private void chooseDirection(int chunkI, int chunkK){	
		//BUKKIT PORT - comment out below block
		boolean[] exploredChunk = new boolean[4];
		exploredChunk[0]=world.blockExists(chunkI << 4, 0, (chunkK-1) << 4); //North
		exploredChunk[1]=world.blockExists((chunkI+1) << 4, 0, chunkK << 4); //East
		exploredChunk[2]=world.blockExists(chunkI << 4, 0, (chunkK+1) << 4); //South
		exploredChunk[3]=world.blockExists((chunkI-1) << 4, 0, chunkK << 4); //West

		//pick an explored direction if it exists
		dir=new int[4];
		int randDir=random.nextInt(4);
		
		//BUKKIT PORT - comment out below
		for(dir[0]=(randDir+1)%4; dir[0]!=randDir; dir[0]=(dir[0]+1)%4)
			if(exploredChunk[dir[0]]) break;  //this chunk has been explored so we want to go in this direction

		//Choose axXHand (careful it is opposite the turn direction of the square).
		//if RH direction explored, then turn RH; else turn LH;
		//BUKKIT PORT - comment out below
		//axXHand=2*random.nextInt(2)-1;
		axXHand= exploredChunk[(dir[0]+1)%4] ? -1 : 1;
		dir[1]=(dir[0]-axXHand+4) % 4;
		dir[2]=(dir[1]-axXHand+4) % 4;
		dir[3]=(dir[2]-axXHand+4) % 4;

	}

}










