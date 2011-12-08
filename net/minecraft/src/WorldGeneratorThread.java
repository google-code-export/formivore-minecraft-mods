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
 * WorldGeneratorThread is a thread that generates structures in the Minecraft world.
 * It is intended to serially hand back and forth control with a BuildingExplorationHandler (not to run parallel).
 */

import java.util.Random;

public abstract class WorldGeneratorThread extends Thread {
	public final static int LAYOUT_CODE_NOCODE=-1;
	public final static int LAYOUT_CODE_EMPTY=0,LAYOUT_CODE_WALL=1, LAYOUT_CODE_AVENUE=2, LAYOUT_CODE_STREET=3, LAYOUT_CODE_TOWER=4, LAYOUT_CODE_TEMPLATE=5;
	protected final static int[][] LAYOUT_CODE_OVERRIDE_MATRIX=new int[][]{ //present code=rows, attempted overriding code=columns
																	   		{0,1,1,1,1,1},  //present empty	
																	   		{0,0,0,0,0,0},  //present wall
																	   		{0,0,1,1,0,0},  //present avenue
																	   		{0,0,1,1,1,0},  //present street
																	   		{0,0,0,0,0,0},  //present tower	
																	   		{0,0,0,0,0,0}}; //present template
	public final static char[] LAYOUT_CODE_TO_CHAR=new char[]{' ','#','=','-','@','&'};
	public volatile boolean threadSuspended=false;
	public boolean hasStarted=false;
	public boolean hasTerminated=false;
	
	public World world;
	public Random random;
	public int chunkI, chunkK, TriesPerChunk;
	public double ChunkTryProb;
	public BuildingExplorationHandler master;
	private int min_spawn_height=0, max_spawn_height=127;
	public boolean spawn_surface=true;
	public boolean willBuild=false;
	
	//All WorldGeneratorThreads will have these, even if not used.
	int[] chestTries=null;
	int[][][] chestItems=null;
	public int ConcaveSmoothingScale=10, ConvexSmoothingScale=20, BacktrackLength=9;
	
	//****************************  CONSTRUCTOR - WorldGeneratorThread *************************************************************************************//
	public WorldGeneratorThread(BuildingExplorationHandler master_, World world_, Random random_, int chunkI_, int chunkK_, int TriesPerChunk_, double ChunkTryProb_){
		world=world_;
		random=random_;
		chunkI=chunkI_;
		chunkK=chunkK_;
		TriesPerChunk=TriesPerChunk_;
		ChunkTryProb=ChunkTryProb_;
		master=master_;
		max_spawn_height=world.field_35472_c-1;
	}
	
	//****************************  FUNCTION - abstract and stub functions  *************************************************************************************//
	public abstract boolean generate(int i0,int j0,int k0) throws InterruptedException;
	
	public boolean isLayoutGenerator(){ return false; }
	public boolean layoutIsClear(int[] pt1, int[] pt2, int layoutCode){ return true; }
	public boolean layoutIsClear(Building building, boolean[][] templateLayout, int layoutCode){ return true; }
	public void setLayoutCode(int[] pt1, int[] pt2, int layoutCode) {}
	public void setLayoutCode(Building building, boolean[][] templateLayout, int layoutCode) {}
	
	//****************************  FUNCTION - run *************************************************************************************//
	public void run(){
		hasStarted=true;
		boolean success=false;
		int tries=0, j0=0, i0=0, k0=0;
		try{
			do{
				if(tries==0 || random.nextDouble()<ChunkTryProb){
					i0=chunkI+random.nextInt(16) + 8;
					k0=chunkK  + random.nextInt(16) + 8;
					if(spawn_surface){
						j0=Building.findSurfaceJ(world,i0,k0,world.field_35472_c-1,true,true)+1;
					}else{
						j0=min_spawn_height+random.nextInt(max_spawn_height - min_spawn_height +1);
					}
					if(j0>0)
						success=generate(i0,j0,k0);
				}
				tries++;
			}while(!success && tries<TriesPerChunk && j0!=Building.HIT_WATER);
		} catch(InterruptedException e){ }
		
		synchronized(master){
			hasTerminated=true;
			threadSuspended=true;
			master.notifyAll();
		}
	}

	//****************************  FUNCTION - setSpawnHeight *************************************************************************************//
	public void setSpawnHeight(int min_spawn_height_, int max_spawn_height_, boolean spawn_surface_){
		min_spawn_height=min_spawn_height_;
		max_spawn_height=max_spawn_height_;
		spawn_surface=spawn_surface_;
	}
	
	//****************************  FUNCTION - exploreArea *************************************************************************************//
	public boolean exploreArea(int[] pt1, int[] pt2, boolean ignoreTerminate) throws InterruptedException{
		int incI=Building.signum(pt2[0]-pt1[0],0), incK=Building.signum(pt2[2]-pt1[2],0);
		for(int chunkI=pt1[0]>>4; ((pt2[0]>>4)-chunkI)*incI > 0; chunkI+=incI)
			for(int chunkK=pt1[2]>>4; ((pt2[2]>>4)-chunkK)*incK > 0; chunkK+=incK)
				if(!queryExplorationHandler(chunkI<<4, chunkK<<4) && !ignoreTerminate) return false;
		return true;
	}
	
	//****************************  FUNCTION - queryExplorationHandler *************************************************************************************//
	public boolean queryExplorationHandler(int i, int k) throws InterruptedException {
    	if(world.blockExists(i,0,k)) return true;
    	
    	//else this chunk does not exist
    	int threadAction=master.queryChunk(i>>4, k>>4);
    	if(threadAction==BuildingExplorationHandler.THREAD_TERMINATE) return false;
    	if(threadAction==BuildingExplorationHandler.THREAD_SUSPEND){
    		//suspend this thread
    		suspendGen();
    	}
    	
    	//check if we are trying to build in a chunk in middle of generation, if so terminate
    	if(master.flushCallChunk!=BuildingExplorationHandler.NO_CALL_CHUNK){
    		if(i>>4==master.flushCallChunk[0] && k>>4==master.flushCallChunk[1])
    			return false;
    	}
    		
    	//MP PORT
    	//world.getChunkProvider().loadChunk(i>>4, k>>4);
    	world.getBlockId(i,0,k); //force world to load this chunk
    	
    	return true;
    }
	
	//****************************  FUNCTION - suspendGen *************************************************************************************//
	public void suspendGen() throws InterruptedException{
		threadSuspended=true;
            synchronized(this) {
                while (threadSuspended){
                	synchronized(master){
                		master.notifyAll();
                	}
                	wait();
                }
            }
	}

}
