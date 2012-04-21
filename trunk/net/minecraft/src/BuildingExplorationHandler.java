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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

//MP PORT
//import net.minecraft.server.MinecraftServer;
//import java.util.logging.Logger;
import net.minecraft.client.Minecraft;


//Guide for Multiplayer Port
/*
1)use Eclipse's Refactor->Rename to rename mod_GreatWall,mod_WalledCity, and mod_CARuins to PopulatorGreatWall, PopulatorWalledCity, and PopulatorCARuins
2)Do a search for the string 'MP PORT' and make replacements
3)Fix any remaining errors, should be commented or self-evident
4)If necessary, add hooks to World.java, ChunkProviderGenerate.java and ChunkProviderHell.java:

	a)Declare the following variables at the beginning of World.java:
    public PopulatorWalledCity populatorWalledCity;
    public PopulatorGreatWall populatorGreatWall;
    public PopulatorCARuins populatorCARuins;

	b)Add the following lines to the end of the tick() function in World.java:
	if(populatorWalledCity!=null) populatorWalledCity.doOnTick(this);
    if(populatorGreatWall!=null) populatorGreatWall.doOnTick(this);
    if(populatorCARuins!=null) populatorCARuins.doOnTick(this);
    
    c)Add the following lines to the end of the populate() function in ChunkProviderGenerate.java 
    if(worldObj.populatorWalledCity==null) worldObj.populatorWalledCity=new PopulatorWalledCity();
    if(worldObj.populatorGreatWall==null){
    	worldObj.populatorGreatWall=new PopulatorGreatWall();
    	worldObj.populatorGreatWall.master=worldObj.populatorWalledCity;
   	}
   	if(worldObj.populatorCARuins==null) {
        worldObj.populatorCARuins=new PopulatorCARuins();
        worldObj.populatorCARuins.master=worldObj.populatorWalledCity;
    }
    worldObj.populatorWalledCity.generateSurface(worldObj, rand, par2, par3);
    worldObj.populatorGreatWall.generateSurface(worldObj, rand, par2, par3);
    worldObj.populatorCARuins.generateSurface(worldObj, rand, par2, par3);
    
    d)Add the following lines to the end of the populate() function in ChunkProviderHell.java.
    if(worldObj.populatorWalledCity==null) worldObj.populatorWalledCity=new PopulatorWalledCity();
    if(worldObj.populatorGreatWall==null){
    	worldObj.populatorGreatWall=new PopulatorGreatWall();
    	worldObj.populatorGreatWall.master=worldObj.populatorWalledCity;
   	}
   	if(worldObj.populatorCARuins==null) {
        worldObj.populatorCARuins=new PopulatorCARuins();
        worldObj.populatorCARuins.master=worldObj.populatorWalledCity;
    }
    worldObj.populatorWalledCity.generateSurface(worldObj, hellRNG, par2, par3);
    worldObj.populatorGreatWall.generateSurface(worldObj, hellRNG, par2, par3); 
    worldObj.populatorCARuins.generateSurface(worldObj, hellRNG, par2, par3);

 */

public abstract class BuildingExplorationHandler extends BaseMod {
	protected final static int MAX_TRIES_PER_CHUNK=10;
	protected final static int CHUNKS_AT_WORLD_START=256;
	public final static int MAX_CHUNKS_PER_TICK=1;
	public final static int[] NO_CALL_CHUNK=null;
	private final static int MIN_CHUNK_SEPARATION_FROM_PLAYER=6;
	
	public final static String VERSION_STRING="v2.3.0";
	public final static String GREAT_WALL_MOD_STRING="mod_GreatWall "+VERSION_STRING;
	public final static String WALLED_CITY_MOD_STRING="mod_WalledCity "+VERSION_STRING;
	
	protected final static String RESOURCES_FOLDER_NAME="resources";
	
	protected final static String SAVES_FOLDER_NAME="saves";
					
	//MP PORT
	//protected final static File BASE_DIRECTORY=new File(".");
	protected final static File BASE_DIRECTORY=Minecraft.getMinecraftDir();
	protected final static File SAVES_DIRECTORY=new File(BASE_DIRECTORY,"saves");
	protected final static File RESOURCES_DIRECTORY=new File(BASE_DIRECTORY,"resources");
	
	public BuildingExplorationHandler master=null;
	protected long explrWorldCode;
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
	//public float GlobalChallengeSlider=1.0F;
	
	//Humans+ reflection fields
 	Constructor h_EntityFlagConstr=null;
 	Method updateFlagMethod=null;
 	Field h_EntityFlagFlagfFld=null;
 	Object enumAssassinObj=null, enumRogueObj=null, enumBanditObj=null, enumPeacefulObj=null, enumMilitiaObj=null, enumShadowObj=null;
 	boolean humansPlusLoaded=false;
	
 	int[] chestTries=new int[]{4,6,6,6};
	int[][][] chestItems=new int[][][]{null,null,null,null};
 	
	//BUKKIT PORT / MP PORT - uncomment
	//public static Logger logger=MinecraftServer.logger;
	public Minecraft mc=ModLoader.getMinecraftInstance();
	
	
	
	
	public String getVersion(){ return VERSION_STRING;}
	public void load(){} //should things be done here instead of in constructors? eh.
	abstract public void updateWorldExplored(World world);
	abstract public void loadDataFiles();
	abstract public void generate(World world, Random random, int i, int k);
	
	
	
	//****************************  FUNCTION - isGeneratorStillValid *************************************************************************************//
	//override this with e.g. the walled city generator
	public boolean isGeneratorStillValid(WorldGeneratorThread wgt){
		return true;
	}
	
	//****************************  FUNCTION - OnTickInGame *************************************************************************************//
	//MP PORT - comment out this function
	@Override
	public boolean onTickInGame(float f, Minecraft minecraft) {
		doOnTick(minecraft.theWorld);
		return true;
	}
	
	//****************************  FUNCTION - doOnTick *************************************************************************************//
	public void doOnTick(World tickWorld){
		if(this==master){
			updateWorldExplored(tickWorld);
			flushGenThreads(NO_CALL_CHUNK);
			runWorldGenThreads();
		}
	}
	
	
	//****************************  FUNCTION - GenerateSurface  *************************************************************************************//
	//BUKKIT PORT
	//public void populate(World world, Random random, Chunk source){
	//	int chunkI=source.getX(), chunkK=source.getZ();
	public void generateSurface( World world, Random random, int i, int k ) {
		if(errFlag) return;
		updateWorldExplored(world);
		chunksExploredFromStart++;
		
		//Put flushGenThreads before the exploreThreads enqueues and include the callChunk argument.
		//This is to avoid putting mineral deposits in cities etc.
		if(this==master && isCreatingDefaultChunks)
			flushGenThreads(new int[]{i,k});
		
		generate(world,random,i,k);
	}
	
	public void generateNether( World world, Random random, int chunkI, int chunkK ) {
		generateSurface(world,random,chunkI,chunkK);
	}
	
	//****************************  FUNCTION - modsLoaded *************************************************************************************//
	//Load templates after mods have loaded so we can check whether any modded blockIDs are valid
	//MP PORT - comment out this function
	@Override
	public void modsLoaded(){
		if(this.toString().equals(WALLED_CITY_MOD_STRING)){
			master=this;
			if(!dataFilesLoaded){
				initializeHumansPlusReflection();
			 	loadDataFiles();
			}
		}
		else{
			//see if the walled city mod is loaded. If it is, make it load its templates (if not already loaded) and then combine explorers.
			for(BaseMod mod : (List<BaseMod>)ModLoader.getLoadedMods()){
				if(mod.toString().equals(WALLED_CITY_MOD_STRING)){
					BuildingExplorationHandler wcm=(BuildingExplorationHandler)mod;
					if(!wcm.dataFilesLoaded) wcm.modsLoaded();
					if(!wcm.errFlag){
						master=wcm;
						System.out.println("Combining chunk explorers for "+toString()+" and "+master.toString()+".");
					}
					break;
			}}
			if(master==null) master=this;
			initializeHumansPlusReflection();
			loadDataFiles();
		}
	}
	
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
	
	//****************************  FUNCTION - setNewWorld *************************************************************************************//
	public void setNewWorld(World world_,String newWorldStr){
		world=world_;
		explrWorldCode=Building.getWorldCode(world);
		chunksExploredThisTick=0;
		chunksExploredFromStart=0;
		if(world.isNewWorld && world.worldInfo.getWorldTime()==0){
			isCreatingDefaultChunks=true;
		}
		logOrPrint(newWorldStr);
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
	//query chunk should be called from the WorldGeneratorThread wgt.
	public boolean queryExplorationHandlerForChunk(int chunkI,int chunkK, WorldGeneratorThread wgt) throws InterruptedException{
		//MP PORT
		/*
		//SMP - world.chunkProvider.chunkExists(chunkI, chunkK) calls ChunkProviderServer.java which returns id2ChunkMap.containsKey(ChunkCoordIntPair.chunkXZ2Int(i, j));
		//check world.chunkProvider.chunkExists before doing explrStartChunk check for SMP.
		//not sure why, but if we do it after we are unable to build at all, or unable to build away from origin??
		if(world.chunkProvider.chunkExists(chunkI, chunkK)) return true;
		if(chunksExploredFromStart==0) {
			explrStartChunkI=chunkI;
			explrStartChunkK=chunkK;
		}
		if(Math.abs(chunkI - explrStartChunkI) > max_exploration_distance
		   || Math.abs(chunkK - explrStartChunkK) > max_exploration_distance){
			return false;
		}
		 */
		int iChunkHome=(isCreatingDefaultChunks || mc.thePlayer==null) ? 0 : (int)mc.thePlayer.posX>>4;
		int kChunkHome=(isCreatingDefaultChunks || mc.thePlayer==null) ? 0 : (int)mc.thePlayer.posZ>>4;
		if((Math.abs(chunkI - iChunkHome) > max_exploration_distance 
		 || Math.abs(chunkK - kChunkHome) > max_exploration_distance)){
				return false;
		}
		if(mc.thePlayer!=null){
			//System.out.println("Thread "+Thread.currentThread().getName()+"player=("+(((int)mc.thePlayer.posX)>>4)+","+(((int)mc.thePlayer.posZ)>>4)+"), chunk=("+chunkI+","+chunkK+").");
			if( Math.abs(chunkI-((int)mc.thePlayer.posX)>>4) < MIN_CHUNK_SEPARATION_FROM_PLAYER 
			 && Math.abs(chunkK-((int)mc.thePlayer.posZ)>>4) < MIN_CHUNK_SEPARATION_FROM_PLAYER){ //try not to bury the player alive
				System.out.println("Terminating "+Thread.currentThread().getName()+" generation thread, too close to player.\n "+Thread.currentThread().getId()+". Player=("+(((int)mc.thePlayer.posX>>4))+","+(((int)mc.thePlayer.posZ>>4))+"), queriedChunk=("+chunkI+","+chunkK+").");
				return false;
			}
		}
		//SSP - world.chunkProvider.chunkExists calls ChunkProvider.java which returns chunkMap.containsKey(ChunkCoordIntPair.chunkXZ2Int(i, j));
		if(world.chunkProvider.chunkExists(chunkI, chunkK)) return true;
		
		
		//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		//OK, we've now failed world.chunkProvider.chunkExists(chunkI, chunkK), so we will have to load or generate this chunk
		//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		
		if(chunksExploredThisTick > (isFlushingGenThreads ? CHUNKS_AT_WORLD_START : MAX_CHUNKS_PER_TICK)){
			//suspend the thread if we've exceeded our quota of chunks to load for this tick
			wgt.suspendGen();
		}
		chunksExploredThisTick++;

		
		if(flushCallChunk!=NO_CALL_CHUNK){
    		if(chunkI==flushCallChunk[0] && chunkK==flushCallChunk[1])
    			return false;
    	}
		
		//SSP - world.chunkProvider.provideChunk calls ChunkProvider.java which returns (Chunk)chunkMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(i, j));
		//       or loadChunk(i, j); if lookup fails. Since we already failed chunkExists(chunkI, chunkK) we could go directly to loadChunk(i,j);
		//SMP - world.chunkProvider.loadChunk calls ChunkProviderServer.java which looks up id2ChunkMap.getValueByKey(l), 
		//       returns this if it exists else calls serverChunkGenerator.provideChunk(i, j);
		//OK we are going to use world.chunkProvider.loadChunk for both SSP and SMP, should test to see if this works
    	world.chunkProvider.loadChunk(chunkI, chunkK);
    	//world.chunkProvider.provideChunk(chunkI, chunkK);
		return true;
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
			}
			isFlushingGenThreads=true;
			flushCallChunk=callChunk;
			while(exploreThreads.size() > 0) 
				doOnTick(world);
			
			isFlushingGenThreads=false;
			isCreatingDefaultChunks=false;
			isAboutToFlushGenThreads=false;
			flushCallChunk=NO_CALL_CHUNK;
		}
	}
	
	//****************************  FUNCTION - runWorldGenThreads *************************************************************************************//
	protected void runWorldGenThreads(){
		ListIterator<WorldGeneratorThread> itr=(ListIterator<WorldGeneratorThread>)((LinkedList<WorldGeneratorThread>)exploreThreads.clone()).listIterator();
		
		while(itr.hasNext() && (isFlushingGenThreads  || chunksExploredThisTick < MAX_CHUNKS_PER_TICK)){
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
			chestTries[triesIdx]=readIntParam(lw,1,":",br.readLine());
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
	
	//****************************************  FUNCTIONS - error handling parameter readers  *************************************************************************************//
	public static int readIntParam(PrintWriter lw,int defaultVal,String splitString, String read){
		try{
			defaultVal=Integer.parseInt(read.split(splitString)[1].trim());
		} catch(Exception e) { 
			lw.println("Error parsing int: "+e.toString());
			lw.println("Using default "+defaultVal+". Line:"+read); 
		}
		return defaultVal;
	}

	public static float readFloatParam(PrintWriter lw,float defaultVal,String splitString, String read){
		try{
			defaultVal=Float.parseFloat(read.split(splitString)[1].trim());
		} catch(Exception e) { 
			lw.println("Error parsing double: "+e.toString());
			lw.println("Using default "+defaultVal+". Line:"+read); 
		}
		return defaultVal;
	}
	
	//if an integer ruleId: try reading from rules and return.
	//If a rule: parse the rule, add it to rules, and return.
	public TemplateRule readRuleIdOrRule(String splitString, String read, TemplateRule[] rules) throws Exception{
		String postSplit=read.split(splitString)[1].trim();
		try{
			int ruleId=Integer.parseInt(postSplit);
			return rules[ruleId];
		} catch(NumberFormatException e) { 
			TemplateRule r=new TemplateRule(postSplit,this,false);
			return r;
		}catch(Exception e) { 
			throw new Exception("Error reading block rule for variable: "+e.toString()+". Line:"+read);
		}
	}
	
	public static int[] readNamedCheckList(PrintWriter lw,int[] defaultVals,String splitString, String read, String[] names, String allStr){
		if(defaultVals==null || names.length!=defaultVals.length) defaultVals=new int[names.length];
		try{
			int[] newVals=new int[names.length];
			for(int i=0;i<newVals.length;i++) newVals[i]=0;
			if((read.split(splitString)[1]).trim().toUpperCase().equals(allStr)){
				for(int i=0;i<newVals.length;i++) newVals[i]=1;
			}else{
				for(String check : (read.split(splitString)[1]).split(",")){
					boolean found=false;
					for(int i=0;i<names.length;i++){
						if(names[i].toLowerCase().equals(check.trim().toLowerCase())){
							found=true;
							newVals[i]++;
						}
					}
					if(!found) 
						lw.println("Warning, named checklist item not found:"+check+". Line:"+read);
				}
			}	
			return newVals;
		}catch(Exception e) { 
			lw.println("Error parsing checklist input: "+e.toString());
			lw.println("Using default. Line:"+read); 
		}
		return defaultVals;
	}

	
	public static int[] readIntList(PrintWriter lw,int[] defaultVals,String splitString,  String read){
		try{
			String[] check = (read.split(splitString)[1]).split(",");
			int[] newVals=new int[check.length];

			for(int i=0;i<check.length;i++){
				int val=Integer.parseInt(check[i].trim());
				newVals[i]=val;
			}
			return newVals;

		}catch(Exception e) { 
			lw.println("Error parsing intlist input: "+e.toString());
			lw.println("Using default. Line:"+read); 
		}
		return defaultVals;
	}

	
	public static ArrayList<byte[][]> readAutomataList(PrintWriter lw, String splitString,String read){
		ArrayList<byte[][]> rules=new ArrayList<byte[][]>();
		String[] ruleStrs =(read.split(splitString)[1]).split(",");
		for(String ruleStr : ruleStrs){
			byte[][] rule=BuildingCellularAutomaton.parseCARule(ruleStr.trim(),lw);
			if(rule!=null) rules.add(rule);
		}
		if(rules.size()==0) return null;
		return rules;
	}
	
}
