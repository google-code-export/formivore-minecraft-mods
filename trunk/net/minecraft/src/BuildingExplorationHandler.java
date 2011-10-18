package net.minecraft.src;
//By formivore 2011 for Minecraft Beta.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
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
	
	public final static String VERSION_STRING="v2.1.2";
	public final static String GREAT_WALL_MOD_STRING="mod_GreatWall "+VERSION_STRING;
	public final static String WALLED_CITY_MOD_STRING="mod_WalledCity "+VERSION_STRING;
	
	
	public int Smooth1=10, Smooth2=20, Backtrack=9;
	
	protected boolean isCreatingDefaultChunks=false, isFlushingGenThreads=false, isAboutToFlushGenThreads=false;
	protected boolean errFlag=false;
	protected LinkedList<int[]> lightingList=new LinkedList<int[]>();
	protected int max_exploration_distance;
	protected int chunksExploredThisTick=0, chunksExploredFromStart=0;
	private int explrStartChunkI, explrStartChunkK;
	public World world;
	protected LinkedList<WorldGeneratorThread> exploreThreads=new LinkedList<WorldGeneratorThread>();
	protected String loadingMessage="";
	
	//BUKKIT PORT / MP PORT - uncomment
	//public static Logger logger=MinecraftServer.logger;
	public Minecraft mc=ModLoader.getMinecraftInstance();
	
	public String Version(){ return VERSION_STRING;}	
	abstract public void updateWorldExplored(World world);
	abstract public boolean isGeneratorStillValid(WorldGeneratorThread wgt);
	abstract public void combineExploreThreads(BuildingExplorationHandler that);
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
			System.out.println("kilt a zombie");
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
	protected void flushGenThreads(){
		if(isFlushingGenThreads) return;
		
		//MP-PORT: delete mc.thePlayer!=null condition
		if(!isCreatingDefaultChunks && mc.thePlayer!=null && !isAboutToFlushGenThreads && chunksExploredFromStart > 2*CHUNKS_AT_WORLD_START-15){
			String flushAnnouncement=(2*CHUNKS_AT_WORLD_START)+" chunks queued to explore this wave, pausing to process.";
			mc.thePlayer.addChatMessage(flushAnnouncement);
			logOrPrint(flushAnnouncement);
			isAboutToFlushGenThreads=true;
		}
		
		if(chunksExploredFromStart>= (isCreatingDefaultChunks ? CHUNKS_AT_WORLD_START-1 : 2*CHUNKS_AT_WORLD_START)){
			if(!(Thread.currentThread() instanceof WorldGeneratorThread)){ //don't want to flush unless current thread is main thread
			
				if(isCreatingDefaultChunks){
					//MP PORT - comment out below line
					mc.loadingScreen.displayLoadingString(loadingMessage);
					//mc.loadingScreen.printText("Generating cities");
				}
				isFlushingGenThreads=true;
				while(exploreThreads.size() > 0) 
					OnTickInGame(0F,mc);
				
				isFlushingGenThreads=false;
				isCreatingDefaultChunks=false;
				isAboutToFlushGenThreads=false;
			}
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
			chestTries[triesIdx]=WallStyle.readIntParam(lw,1,":",br.readLine());
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
