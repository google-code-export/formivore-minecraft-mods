package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Modloader handle for Great Wall Mod, reads in from SETTINGS_FILE.
 */

import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.*;

//MP PORT
//import net.minecraft.server.MinecraftServer;
//import java.util.logging.Logger;
import net.minecraft.client.Minecraft;

//BUKKIT PORT
//public class mod_GreatWall extends BlockPopulator implements IBuildingExplorationHandler 

public class mod_GreatWall extends BuildingExplorationHandler
{
	public final static File SETTINGS_FILE, STYLES_DIRECTORY, LOG_FILE;
	private final static int MAX_EXPLORATION_DISTANCE=30;
	public final static float ACCEPT_ALPHA=50.0F;
	static{
		//BUKKIT PORT / MP PORT
		//File baseDir=new File(".");
		File baseDir=Minecraft.getMinecraftDir();
		SETTINGS_FILE=new File(baseDir,"GreatWallModSettings.txt");
		LOG_FILE=new File(baseDir,"great_wall_log.txt");
		STYLES_DIRECTORY=new File(new File(baseDir,"resources"),"greatwall");
	}
	

	//USER MODIFIABLE PARAMETERS, values below are defaults
	public float ChunkTryProb=0.0015F;
	public int TriesPerChunk=1;
	//public int hL=500;
	public float CurveBias=0.0F;
	public int LengthBiasNorm=200;
	

	//DATA VARIABLES
	public BuildingExplorationHandler walledCityMod=null;
	public ArrayList<WallStyle> wallStyles=null;
	private long explrWorldCode;
	private PrintWriter lw=null;
	//public LinkedList<WorldGeneratorThread> exploreThreads;
	
	public int[] placedCoords=null;
	public World placedWorld=null;
	//public final Block surveyorsRod= new BlockSurveyorsRod(131, 0,this).setHardness(2.0F).setResistance(5.0F).setBlockName("SureveyorsRod");
	
	
	//BUKKIT PORT / MP PORT - uncomment
	//public static Logger logger=MinecraftServer.logger;

	//****************************  CONSTRUCTOR - mod_GreatWall*************************************************************************************//
	public mod_GreatWall() {
		//ModLoader.RegisterBlock(surveyorsRod);
	    //magicWall.blockIndexInTexture = ModLoader.addOverride("/terrain.png", "/texturetest.png");
			
		//ModLoader.AddRecipe(new ItemStack(surveyorsRod,8), new Object[]{ "##", "##", Character.valueOf('#'), Block.dirt});
		
		ModLoader.SetInGameHook(this,true,true);
		
		//MP PORT - uncomment
		//loadDataFiles();
	}
	
	@Override
	public String toString(){
		return GREAT_WALL_MOD_STRING;
	}
	
	//****************************  FUNCTION - ModsLoaded *************************************************************************************//
	//Load templates after mods have loaded so we can check whether any modded blockIDs are valid
	public void ModsLoaded(){
		//see if the walled city mod is loaded. If it is, make it load its templates (if not already loaded) and then combine explorers.
		for(BaseMod mod : (List<BaseMod>)ModLoader.getLoadedMods()){
			if(mod.toString().equals(WALLED_CITY_MOD_STRING)){
				BuildingExplorationHandler wcp=(BuildingExplorationHandler)mod;
				if(!wcp.dataFilesLoaded) wcp.loadDataFiles();
				if(!wcp.errFlag){
					walledCityMod=wcp;
					System.out.println("Combining chunk explorers for "+toString()+" and "+walledCityMod.toString()+".");
				}
				break;
		}}
		
		loadDataFiles();
	}
	
	//****************************  FUNCTION - loadDataFiles *************************************************************************************//
	public void loadDataFiles(){
		try {
			//read and check values from file
			lw= new PrintWriter( new BufferedWriter( new FileWriter(LOG_FILE) ) );
			loadingMessage="Generating walls";
			logOrPrint("Loading options and templates for the Great Wall Mod.");
			getGlobalOptions();

			max_exploration_distance=MAX_EXPLORATION_DISTANCE;
			
			wallStyles=WallStyle.loadWallStylesFromDir(STYLES_DIRECTORY,lw);

			lw.println("\nTemplate loading complete.");
			lw.println("Probability of generation attempt per chunk explored is "+ChunkTryProb+", with "+TriesPerChunk+" tries per chunk.");
			if(ChunkTryProb <0.000001) errFlag=true;
		} catch( Exception e ) {
			errFlag=true;
			logOrPrint( "There was a problem loading the great wall mod: "+ e.getMessage() );
			lw.println( "There was a problem loading the great wall mod: "+ e.getMessage() );
			e.printStackTrace();
		}finally{ if(lw!=null) lw.close(); }
		dataFilesLoaded=true;
	}

	//****************************  FUNCTION - updateWorldExplored *************************************************************************************//
	public synchronized void updateWorldExplored(World world_) {
		if(Building.getWorldCode(world_)!=explrWorldCode){
			world=world_;
			explrWorldCode=Building.getWorldCode(world);
			chunksExploredThisTick=0;
			chunksExploredFromStart=0;
			if(world.isNewWorld && world.worldInfo.getWorldTime()==0) {
				isCreatingDefaultChunks=true;
			}
			
			if(walledCityMod==null){
				//kill zombies
				for(WorldGeneratorThread wgt: exploreThreads) killZombie(wgt);
				exploreThreads=new LinkedList<WorldGeneratorThread>();
			} else {
				walledCityMod.updateWorldExplored(world);
				exploreThreads=walledCityMod.exploreThreads;
			}
			
			
			logOrPrint("Starting to survey a world for wall generation...");
		}
	}
	
	//****************************  FUNCTION - isGeneratorStillValid *************************************************************************************//
	public boolean isGeneratorStillValid(WorldGeneratorThread wgt){
		return true;
	}

	//****************************  FUNCTION - GenerateSurface  *************************************************************************************//
	//BUKKIT PORT
	//public void populate(World world, Random random, Chunk source){
	//	int chunkI=source.getX(), chunkK=source.getZ();
	public void GenerateSurface( World world, Random random, int i, int k ) {
		if(errFlag) return;
		updateWorldExplored(world);
		chunksExploredFromStart++;
		
		if(random.nextFloat() < ChunkTryProb)
			exploreThreads.add(new WorldGenGreatWall(this,walledCityMod==null ? this : walledCityMod, world, random, i, k,TriesPerChunk, ChunkTryProb));
		
		if(walledCityMod==null) flushGenThreads();
	}
	
	public void GenerateNether( World world, Random random, int chunkI, int chunkK ) {
		GenerateSurface(world,random,chunkI,chunkK);
	}
	
	//****************************  FUNCTION - OnTickInGame  *************************************************************************************//
	//MP Port
	//public boolean OnTickInGame() {
	@Override
	public boolean OnTickInGame(float tick, net.minecraft.client.Minecraft game) {
		if(walledCityMod!=null) return true;
		//if(exploreThreads.size()==0) doQueuedLighting();
		flushGenThreads();
		runWorldGenThreads();
		return true;
	}

	//****************************  FUNCTION - getGlobalOptions  *************************************************************************************//
	private void getGlobalOptions(){
		if(SETTINGS_FILE.exists()){
			BufferedReader br = null;
			try{
				br=new BufferedReader( new FileReader(SETTINGS_FILE) );
				String read = br.readLine();
				lw.println("Getting global options...");    
	
				while( read != null ) {
					if(read.startsWith( "ChunkTryProb" )) ChunkTryProb = WallStyle.readFloatParam(lw,ChunkTryProb,":",read);
					if(read.startsWith( "TriesPerChunk" )) TriesPerChunk = WallStyle.readIntParam(lw,TriesPerChunk,":",read);
					if(read.startsWith( "CurveBias" )) CurveBias = WallStyle.readFloatParam(lw,CurveBias,":",read);
					if(read.startsWith( "LengthBiasNorm" )) LengthBiasNorm = WallStyle.readIntParam(lw,LengthBiasNorm,":",read);
					if(read.startsWith( "Smooth1" )) Smooth1 = WallStyle.readIntParam(lw,Smooth1,":",read);
					if(read.startsWith( "Smooth2" )) Smooth2 = WallStyle.readIntParam(lw,Smooth2,":",read);
					if(read.startsWith( "Backtrack" )) Backtrack = WallStyle.readIntParam(lw,Backtrack,":",read);
					
					readChestItemsList(lw,read,br);
					
					read = br.readLine();
				}
				
				if(TriesPerChunk > MAX_TRIES_PER_CHUNK) TriesPerChunk = MAX_TRIES_PER_CHUNK;
				if(CurveBias<0.0) CurveBias=0.0F;
				if(CurveBias>1.0) CurveBias=1.0F;
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }
		}
		else{
			copyDefaultChestItems();
			PrintWriter pw=null;
			try{
				pw=new PrintWriter( new BufferedWriter( new FileWriter(SETTINGS_FILE) ) );
				pw.println("<-README: put this file in the main minecraft folder, e.g. C:\\Users\\Administrator\\AppData\\Roaming\\.minecraft\\->");
				pw.println();
				pw.println("<-ChunkTryProb Controls how likely walls are to appear. Should be between 0.0 and 1.0. Lower to make less common->");
				pw.println("<-TriesPerChunk allows multiple attempts per chunk. Only change from 1 if you want very dense walls!->");
				pw.println("ChunkTryProb:"+ChunkTryProb);
				pw.println("TriesPerChunk:"+TriesPerChunk);
				pw.println();
				pw.println("<Pathfinding>");
				pw.println("<-Smooth1 and Smooth2 control concave and convex smoothing respectively.>");
				pw.println("<-Backtrack - length of backtracking if a dead end is hit>");
				pw.println("<-CurveBias - strength of the bias towards curvier walls. Should be greater than 0.0. >");
				pw.println("<-LengthBiasNorm - wall length at which there is no penalty for generation>");
				pw.println("Smooth1:"+Smooth1);
				pw.println("Smooth2:"+Smooth2);
				pw.println("Backtrack:"+Backtrack);
				pw.println("CurveBias:"+CurveBias);
				pw.println("LengthBiasNorm:"+LengthBiasNorm);
				pw.println();
				pw.println();
				printDefaultChestItems(pw);
			}
			catch(Exception e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close();}

		}

	}

}
