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
 * BuildingExplorationHandler is a abstract superclass for mod_WalledCity and mod_GreatWall.
 * It loads settings files and runs WorldGeneratorThreads.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

//MP PORT
//import net.minecraft.server.MinecraftServer;
//import java.util.logging.Logger;
import net.minecraft.client.Minecraft;


//Guide for MP PORT
/*
1)use Eclipse's Refactor->Rename to rename mod_GreatWall to PopulatorGreatWall and mod_WalledCity to PopulatorWalledCity
2)Do a search for the string 'MP PORT' and make replacements
3)Fix any remaining errors, should be commented or self-evident
4)If necessary, add hooks to World.java, ChunkProviderGenerate.java and ChunkProviderHell.java:

	a)Add the following lines to the end of the tick() function in World.java:
	if(populatorWalledCity!=null) 
        populatorWalledCity.OnTickInGame();
    if(populatorGreatWall!=null) 
        populatorGreatWall.OnTickInGame();
        
    b)Declare the following variables at the end of World.java:
    public PopulatorWalledCity populatorWalledCity;
    public PopulatorGreatWall populatorGreatWall;
    
    c)Add the following lines to the end of the populate() function in ChunkProviderGenerate.java 
    if(worldObj.populatorWalledCity==null) worldObj.populatorWalledCity=new PopulatorWalledCity();
    if(worldObj.populatorGreatWall==null) worldObj.populatorGreatWall=new PopulatorGreatWall();
    worldObj.populatorWalledCity.GenerateSurface(worldObj, rand, k, l);
    worldObj.populatorGreatWall.GenerateSurface(worldObj, rand, k, l);
    
    d)Add the following lines to the end of the populate() function in ChunkProviderHell.java.
    if(worldObj.populatorWalledCity==null) worldObj.populatorWalledCity=new PopulatorWalledCity();
    if(worldObj.populatorGreatWall==null) worldObj.populatorGreatWall=new PopulatorGreatWall();
    worldObj.populatorWalledCity.GenerateSurface(worldObj, hellRNG, k, l);
    worldObj.populatorGreatWall.GenerateSurface(worldObj, hellRNG, k, l); 

 */

public abstract class BuildingExplorationHandler extends BaseMod {
	public final static int THREAD_CONTINUE=0, THREAD_SUSPEND=1, THREAD_TERMINATE=2;
	protected final static int MAX_TRIES_PER_CHUNK=10;
	protected final static int CHUNKS_AT_WORLD_START=256;
	public final static int MAX_CHUNKS_PER_TICK=1;
	public final static int[] NO_CALL_CHUNK=null;
	
	public final static String VERSION_STRING="v2.1.2";
	public final static String GREAT_WALL_MOD_STRING="mod_GreatWall "+VERSION_STRING;
	public final static String WALLED_CITY_MOD_STRING="mod_WalledCity "+VERSION_STRING;
	
	
	public int ConcaveSmoothingScale=10, ConvexSmoothingScale=20, BacktrackLength=9;
	
	protected boolean isCreatingDefaultChunks=false, isFlushingGenThreads=false, isAboutToFlushGenThreads=false;
	protected boolean errFlag=false, dataFilesLoaded=false;
	protected LinkedList<int[]> lightingList=new LinkedList<int[]>();
	protected int max_exploration_distance;
	protected int chunksExploredThisTick=0, chunksExploredFromStart=0;
	private int explrStartChunkI, explrStartChunkK;
	public World world;
	protected LinkedList<WorldGeneratorThread> exploreThreads=new LinkedList<WorldGeneratorThread>();
	protected String loadingMessage="";
	public int[] flushCallChunk=NO_CALL_CHUNK;
	public PrintWriter lw=null;
	
	//Humans+ reflection fields
 	Constructor h_EntityFlagConstr=null;
 	Method updateFlagMethod=null;
 	Field h_EntityFlagFlagfFld=null;
 	Object enumAssassinObj=null, enumRogueObj=null, enumBanditObj=null, enumPeacefulObj=null, enumMilitiaObj=null, enumShadowObj=null;
 	boolean humansPlusLoaded=false;
	
	//BUKKIT PORT / MP PORT - uncomment
	//public static Logger logger=MinecraftServer.logger;
	public Minecraft mc=ModLoader.getMinecraftInstance();
	
	public String Version(){ return VERSION_STRING;}	
	abstract public void updateWorldExplored(World world);
	abstract public boolean isGeneratorStillValid(WorldGeneratorThread wgt);
	abstract public void loadDataFiles();
	public boolean OnTickInGame() { return true;} //for the  multiplayer port, don't have to do anything here
	
	int[] chestTries=new int[]{4,6,6,6};
	int[][][] chestItems=new int[][][]{null,null,null,null};
	
	//****************************  FUNCTION - killZombie *************************************************************************************//
	public void killZombie(WorldGeneratorThread wgt){
		if(wgt.hasStarted && wgt.isAlive()){
			synchronized(this){
				//wgt.killMe=true;
				synchronized(wgt){
					wgt.interrupt();
					wgt.notifyAll();
				}
				joinAtSuspension(wgt);
			}
			System.out.println("Killed a zombie thread.");
		}
	}
	
	//****************************  FUNCTION - initializeHumansPlusReflection *************************************************************************************//
	public void initializeHumansPlusReflection(){
	 	try{
	 		Class h_EntityFlagCls = Class.forName("h_EntityFlag");
	 		Class h_SettingFlagTypeCls = Class.forName("h_SettingsFlagTypes");
	 		Class[] h_EntityFlagPartypes = new Class[]{Class.forName("net.minecraft.src.World"),Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE};
			h_EntityFlagConstr = h_EntityFlagCls.getConstructor(h_EntityFlagPartypes);
			updateFlagMethod = h_EntityFlagCls.getMethod("updateFlag",null);
			h_EntityFlagFlagfFld = h_EntityFlagCls.getField("flag");
			
			//Get the enum values. .get() argument is null since it is a static enum field
			enumAssassinObj=h_SettingFlagTypeCls.getField("Assassin").get(null);
			enumRogueObj=h_SettingFlagTypeCls.getField("Rogue").get(null);
			enumBanditObj=h_SettingFlagTypeCls.getField("Bandit").get(null);
			enumPeacefulObj=h_SettingFlagTypeCls.getField("Peaceful").get(null);
			enumMilitiaObj=h_SettingFlagTypeCls.getField("Militia").get(null);
			enumShadowObj=h_SettingFlagTypeCls.getField("Shadow").get(null);
			humansPlusLoaded=true;
	 	}catch(Throwable e){
	 		
	 	}
	}
	
	//****************************  FUNCTION - queryChunk *************************************************************************************//
	public int queryChunk(int chunkI,int chunkK) {
		//MP PORT
		//if(chunksExploredFromStart==0) {
		//	explrStartChunkI=chunkI;
		//	explrStartChunkK=chunkK;
		//}
		//if(Math.abs(chunkI - explrStartChunkI) > max_exploration_distance
		//|| Math.abs(chunkK - explrStartChunkK) > max_exploration_distance){
		//	return THREAD_TERMINATE;
		//}
		int iChunkHome=(isCreatingDefaultChunks || mc.thePlayer==null) ? 0 : (int)mc.thePlayer.posX>>4;
		int kChunkHome=(isCreatingDefaultChunks || mc.thePlayer==null) ? 0 : (int)mc.thePlayer.posZ>>4;
		if((Math.abs(chunkI - iChunkHome) > max_exploration_distance 
		 || Math.abs(chunkK - kChunkHome) > max_exploration_distance)){
				return THREAD_TERMINATE;
		}
		if(mc.thePlayer!=null && (chunkI==((int)mc.thePlayer.posX)>>4 || chunkK==((int)mc.thePlayer.posZ)>>4)){ //try not to bury the player alive
			return THREAD_TERMINATE;
		}

		
		if(chunksExploredThisTick > (isFlushingGenThreads ? CHUNKS_AT_WORLD_START : MAX_CHUNKS_PER_TICK)){
			return THREAD_SUSPEND;
		}
		chunksExploredThisTick++;
		return THREAD_CONTINUE;
	}
	
	//****************************  FUNCTION - flushGenThreads *************************************************************************************//
	protected void flushGenThreads(int[] callChunk){	
		//announce there is about to be lag because we are about to flush generation threads
		//MP PORT: comment out this block
		if(!isAboutToFlushGenThreads && !isCreatingDefaultChunks && mc.thePlayer!=null && chunksExploredFromStart > 2*CHUNKS_AT_WORLD_START-15){
			String flushAnnouncement=(2*CHUNKS_AT_WORLD_START)+" chunks queued to explore this wave, pausing to process.";
			mc.thePlayer.addChatMessage(flushAnnouncement);
			logOrPrint(flushAnnouncement);
			isAboutToFlushGenThreads=true;
		}
		
		//Must make sure that a)There is only one call to flushGenThreads on the stack at a time
		//                    b)flushGenThreads is only called from the main Minecraft thread.
		//This check is not at the beginning of function because we want to announce we are about to flush no matter what.
		if(isFlushingGenThreads || Thread.currentThread() instanceof WorldGeneratorThread) 
				return;
		
		if(chunksExploredFromStart>= (isCreatingDefaultChunks ? CHUNKS_AT_WORLD_START-1 : 2*CHUNKS_AT_WORLD_START)){
			if(isCreatingDefaultChunks){
				//MP PORT - comment out below line
				mc.loadingScreen.displayLoadingString(loadingMessage);
				//mc.loadingScreen.printText("Generating cities");
			}
			isFlushingGenThreads=true;
			flushCallChunk=callChunk;
			while(exploreThreads.size() > 0) 
				OnTickInGame(0F,mc);
			
			isFlushingGenThreads=false;
			isCreatingDefaultChunks=false;
			isAboutToFlushGenThreads=false;
			flushCallChunk=NO_CALL_CHUNK;
		}
	}
	
	//****************************  FUNCTION - runWorldGenThreads *************************************************************************************//
	protected void runWorldGenThreads(){
		ListIterator<WorldGeneratorThread> itr=(ListIterator<WorldGeneratorThread>)((LinkedList<WorldGeneratorThread>)exploreThreads.clone()).listIterator();
		
		while(itr.hasNext() && chunksExploredThisTick < (isFlushingGenThreads ? CHUNKS_AT_WORLD_START : MAX_CHUNKS_PER_TICK)){
			WorldGeneratorThread wgt=itr.next();
			synchronized(this){
				if(!wgt.hasStarted) 
					wgt.start();
				else{
					synchronized(wgt){
						wgt.threadSuspended=false;
						wgt.notifyAll();
					}
				}
				joinAtSuspension(wgt);
			}
			if(wgt.willBuild && !isFlushingGenThreads) 
				break;
		}
		
		itr=exploreThreads.listIterator();
		while(itr.hasNext()){
			WorldGeneratorThread wgt=itr.next();
			if(wgt.hasStarted){
				if(!wgt.isAlive()) itr.remove();
			}else if(!isGeneratorStillValid(wgt))
				itr.remove();
		}
		
		if(exploreThreads.size()==0) {
			//if(chunksExploredFromStart > 10) System.out.println("Explored "+chunksExploredFromStart+" chunks in last wave.");
			chunksExploredFromStart=0;
		}
		chunksExploredThisTick=0;
	}
	
	
	//****************************  FUNCTION - doQueuedLighting *************************************************************************************//
	//BUKKIT PORT
	//public void queueLighting(int[] pt){}
	//public void doQueuedLighting(World world){}
	/*
	public void queueLighting(int[] pt){
		lightingList.add(pt);
	}
	
	public void doQueuedLighting(){
		//if(lightingList.size()>100 ) logOrPrint("Doing "+lightingList.size()+" queued lighting commands.");
		lightingList=new LinkedList<int[]>();
		while(lightingList.size()>0){
			int[] pt=lightingList.remove();
			world.scheduleLightingUpdate(EnumSkyBlock.Sky,pt[0],pt[1],pt[2],pt[3],pt[4],pt[5]);
			world.scheduleLightingUpdate(EnumSkyBlock.Block,pt[0],pt[1],pt[2],pt[3],pt[4],pt[5]);
		}
	}
	*/
	
	//****************************  FUNCTION - logOrPrint *************************************************************************************//
	public void logOrPrint(String str){
		//BUKKIT PORT / MP PORT
		//logger.info(str);
		System.out.println(str);
	}
	
	//****************************  FUNCTION - chestContentsList *************************************************************************************//
	public void readChestItemsList(PrintWriter lw, String line, BufferedReader br) throws IOException{
		int triesIdx=-1;
		for(int l=0; l<Building.CHEST_TYPE_LABELS.length; l++){
			if(line.startsWith(Building.CHEST_TYPE_LABELS[l])){
				triesIdx=l;
				break;
			}
		}
		
		if(triesIdx!=-1){
			chestTries[triesIdx]=TemplateWall.readIntParam(lw,1,":",br.readLine());
			ArrayList<String> lines=new ArrayList<String>();
			for(line=br.readLine(); !(line==null || line.length()==0); line=br.readLine())
				lines.add(line);
			chestItems[triesIdx]=new int[6][lines.size()];
			for(int n=0; n<lines.size(); n++){
				String[] intStrs=lines.get(n).trim().split(",");
				try{
					chestItems[triesIdx][0][n]=n;
					String[] idAndMeta=intStrs[0].split("-");
					chestItems[triesIdx][1][n]=Integer.parseInt(idAndMeta[0]);
					chestItems[triesIdx][2][n]= idAndMeta.length>1 ? Integer.parseInt(idAndMeta[1]) : 0;
					for(int m=1; m<4; m++) 
						chestItems[triesIdx][m+2][n]=Integer.parseInt(intStrs[m]);
					
					//input checking
					if(chestItems[triesIdx][4][n]<0) chestItems[triesIdx][4][n]=0;
					if(chestItems[triesIdx][5][n]<chestItems[triesIdx][4][n]) 
						chestItems[triesIdx][5][n]=chestItems[triesIdx][4][n];
					if(chestItems[triesIdx][5][n]>64) chestItems[triesIdx][5][n]=64;
				}catch(Exception e){
					lw.println("Error parsing Settings file: "+e.toString());
					lw.println("Line:"+lines.get(n));
				}
			}
		}
	}
	
	protected void copyDefaultChestItems(){
		chestTries=new int[Building.DEFAULT_CHEST_TRIES.length];
		for(int n=0; n<Building.DEFAULT_CHEST_TRIES.length; n++)
			chestTries[n]=Building.DEFAULT_CHEST_TRIES[n];
		chestItems=new int[Building.DEFAULT_CHEST_ITEMS.length][][];
		
		//careful, we have to flip the order of the 2nd and 3rd dimension here
		for(int l=0; l<Building.DEFAULT_CHEST_ITEMS.length; l++){
			chestItems[l]=new int[6][Building.DEFAULT_CHEST_ITEMS[l].length];
			for(int m=0; m<Building.DEFAULT_CHEST_ITEMS[l].length; m++){
				for(int n=0; n<6; n++){
					chestItems[l][n][m]=Building.DEFAULT_CHEST_ITEMS[l][m][n];
		}}}
	}
	
	public void printDefaultChestItems(PrintWriter pw){
		for(int l=0; l<chestItems.length; l++){
			pw.println(Building.CHEST_TYPE_LABELS[l]);
			pw.println("Tries:"+chestTries[l]);
			for(int m=0; m<chestItems[l][0].length; m++){
				pw.print(chestItems[l][1][m]);
				if(chestItems[l][2][m]!=0) pw.print("-"+chestItems[l][2][m]);
				pw.print(","+chestItems[l][3][m]);
				pw.print(","+chestItems[l][4][m]);
				pw.println(","+chestItems[l][5][m]);
			}
			pw.println();
		}
	}
	
	//****************************  FUNCTION - joinAtSuspension *************************************************************************************//
	protected void joinAtSuspension(WorldGeneratorThread wgt){
		while (wgt.isAlive() && !wgt.threadSuspended){
	        try {
					wait();
	        } catch (InterruptedException e){}
		}
		try {
			if(wgt.hasTerminated) wgt.join();
		}catch (InterruptedException e){}
	}
}
