package net.minecraft.src;

import java.util.Random;

public class WorldGenSingleWall extends WorldGeneratorThread{
	private int[] pt;
	private mod_GreatWall gw;
	
	public WorldGenSingleWall (mod_GreatWall gw_, World world_, Random random_, int[] pt_) {
		super(gw_, world_, random_, pt_[0]>>4, pt_[2]>>4, 0, 0.0);
		setName("WorldGenSingleWallThread");
		pt=pt_;
		gw=gw_;
	}
	
	@Override
	public void run(){
		hasStarted=true;
		try{
			generate(pt[0],pt[1],pt[2]);
		} catch(InterruptedException e){ }

		synchronized(explorationHandler){
			hasTerminated=true;
			threadSuspended=true;
			explorationHandler.notifyAll();
		}
	}
	
	@Override
	public boolean generate(int i0, int j0, int k0) throws InterruptedException{
  		WallStyle ws=WallStyle.pickBiomeWeightedWallStyle(gw.wallStyles,world,i0,k0,random,false);
  		BuildingWall wall=new BuildingWall(0,this,ws,Building.DIR_NORTH,Building.R_HAND, ws.MaxL,true,i0,j0,k0);
  		//BuildingWall(int ID_, WorldGeneratorThread wgt_,WallStyle ws_,int dir_,int axXHand_, int maxLength_,int i0_,int j0_, int k0_){
  		
  		wall.setTarget(gw.placedCoords);
  		wall.plan(1,0,ws.MergeWalls ? ws.WWidth : BuildingWall.DEFAULT_LOOKAHEAD,false);
  		//plan(int Backtrack, int startN, int depth, int lookahead, boolean stopAtWall) throws InterruptedException {
  		
  		if(wall.bLength>=wall.y_targ){
  			wall.smooth(gw.Smooth1,gw.Smooth2,true);
  			wall.buildFromTML();
  			wall.buildTowers(true,true,true,false,false);		
			gw.placedCoords=null;
  		}
		
		return true;
	}

}
