package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Modloader handle for Walled City Generator Mod, reads in from SETTINGS_FILE.
 */

import java.util.Random;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

//MP PORT
//import net.minecraft.server.MinecraftServer;
//import java.util.logging.Logger;
import net.minecraft.client.Minecraft;

//BUKKIT PORT
//public class mod_WalledCity extends BlockPopulator implements IBuildingExplorationHandler

public class mod_WalledCity extends BuildingExplorationHandler
{
	public final static int MIN_CITY_LENGTH=40;
	private final static int MAX_EXPLORATION_DISTANCE=30;
	public final static File SETTINGS_FILE, STYLES_DIRECTORY,STREETS_DIRECTORY, LOG_FILE;
	public final static int CITY_TYPE_WALLED=0, CITY_TYPE_UNDERGROUND=1;
	static{
		//BUKKIT PORT / MP PORT
		//File baseDir=new File(".");
		File baseDir=Minecraft.getMinecraftDir();
		SETTINGS_FILE=new File(baseDir,"WalledCitySettings.txt");
		LOG_FILE=new File(baseDir,"walled_city_log.txt");
		STYLES_DIRECTORY=new File(new File(baseDir,"resources"),"walledcity");
		STREETS_DIRECTORY=new File(STYLES_DIRECTORY,"streets");
	}

	//USER MODIFIABLE PARAMETERS, values here are defaults
	public float ChunkTryProb=0.05F, UndergroundChunkTryProb=0.005F;
	public int TriesPerChunk=1;
	public int MinCitySeparation=500, UndergroundMinCitySeparation=250;
	public boolean CityBuiltMessage=true;

	//DATA VARIABLES
	public ArrayList<WallStyle> cityStyles=null, undergroundCityStyles=new ArrayList<WallStyle>();
	private long explrWorldCode;
	private ArrayList<int[]> cityLocations, undergroundCityLocations;
	private HashMap<Long,ArrayList<int[]> > worldCityLocationsMap=new HashMap<Long,ArrayList<int[]> >(),
											undergroundWorldCityLocationsMap=new HashMap<Long,ArrayList<int[]> >();
	private PrintWriter lw=null;
	public LinkedList<int[]> citiesBuiltMessages=new LinkedList<int[]>();
	//private LinkedList<WorldGeneratorThread> exploreThreads=new LinkedList<WorldGeneratorThread>();
	
	//BUKKIT PORT / MP PORT - uncomment
	//public static Logger logger=MinecraftServer.logger;

	//****************************  CONSTRUCTOR - mod_WalledCity  *************************************************************************************//
	public mod_WalledCity() {
		ModLoader.SetInGameHook(this,true,true);
		loadingMessage="Generating cities";
		max_exploration_distance=MAX_EXPLORATION_DISTANCE;
		
		//MP PORT - uncomment
		//loadDataFiles();
	}
	
	@Override
	public String toString(){
		return WALLED_CITY_MOD_STRING;
	}
	
	//****************************  FUNCTION - ModsLoaded *************************************************************************************//
	//Load templates after mods have loaded so we can check whether any modded blockIDs are valid
	public void ModsLoaded(){
		if(!dataFilesLoaded){
			initializeHumansPlusReflection();
		 	loadDataFiles();
		}
	}
	
	//****************************  FUNCTION - loadDataFiles *************************************************************************************//
	public void loadDataFiles(){
		try {
			//read and check values from file
			lw= new PrintWriter( new BufferedWriter( new FileWriter(LOG_FILE ) ));
			
			logOrPrint("Loading options and templates for the Walled City Generator.");
			getGlobalOptions();
			

			cityStyles=WallStyle.loadWallStylesFromDir(STYLES_DIRECTORY,lw);
			WallStyle.loadStreets(cityStyles,STREETS_DIRECTORY,lw);
			for(int m=0; m<cityStyles.size(); m++){
				if(cityStyles.get(m).underground){
					WallStyle uws = cityStyles.remove(m);
					uws.streets.add(uws); //underground cities have no outer walls, so this should be a street style
					undergroundCityStyles.add(uws);
					m--;
			}}

			lw.println("\nTemplate loading complete.");
			lw.println("Probability of generation attempt per chunk explored is "+ChunkTryProb+", with "+TriesPerChunk+" tries per chunk.");
			if(ChunkTryProb <0.000001 && UndergroundChunkTryProb<0.000001) errFlag=true;
		} catch( Exception e ) {
			errFlag=true;
			lw.println( "There was a problem loading the walled city mod: "+e.getMessage() );
			logOrPrint( "There was a problem loading the walled city mod: "+e.getMessage() );
			e.printStackTrace();
		}finally{ if(lw!=null) lw.close(); }

		dataFilesLoaded=true;
	}
	
	//****************************  FUNCTION - cityIsSeparated *************************************************************************************//
	public boolean cityIsSeparated(int i, int k, int cityType){
		ArrayList<int[]> locations = cityType==CITY_TYPE_WALLED ? cityLocations : undergroundCityLocations;
		if(locations ==null) 
			return true;
		for(int [] cityPt : locations ){
			if( Math.abs(cityPt[0]-i) + Math.abs(cityPt[1]-k) < (cityType==CITY_TYPE_WALLED ?  MinCitySeparation : UndergroundMinCitySeparation)){
				return false;
			}
		}
		return true;
	}
	
	//****************************  FUNCTION - addCityLocation *************************************************************************************//
	public void addCityLocation(int i, int k, int cityType){
		if(cityType==CITY_TYPE_UNDERGROUND) undergroundCityLocations.add(new int[]{i,k});
		else cityLocations.add(new int[]{i,k});
	}

	//****************************  FUNCTION - updateWorldExplored *************************************************************************************//
	public void updateWorldExplored(World world_) {
		if(Building.getWorldCode(world_)!=explrWorldCode){
			world=world_;
 			explrWorldCode=Building.getWorldCode(world);
			chunksExploredThisTick=0;
			chunksExploredFromStart=0;
			if(world.isNewWorld && world.worldInfo.getWorldTime()==0){
				isCreatingDefaultChunks=true;
			}
			logOrPrint("Starting to survey a world for city generation...");
			
			//kill zombies
			for(WorldGeneratorThread wgt: exploreThreads) killZombie(wgt);
			exploreThreads=new LinkedList<WorldGeneratorThread>();
			
			if(!worldCityLocationsMap.containsKey(explrWorldCode)){
				worldCityLocationsMap.put(explrWorldCode,new ArrayList<int[]>());
				undergroundWorldCityLocationsMap.put(explrWorldCode,new ArrayList<int[]>());
			}
			cityLocations=worldCityLocationsMap.get(explrWorldCode);
			undergroundCityLocations=undergroundWorldCityLocationsMap.get(explrWorldCode);
		}
	}
	
	//****************************  FUNCTION - isGeneratorStillValid *************************************************************************************//
	public boolean isGeneratorStillValid(WorldGeneratorThread wgt){
		return cityIsSeparated(wgt.chunkI,wgt.chunkK,wgt.spawn_surface ? CITY_TYPE_WALLED : CITY_TYPE_UNDERGROUND);
	}
	
	
	//****************************  FUNCTION - chatCityBuilt *************************************************************************************//
	//BUKKIT PORT / MP PORT
	//public void chatBuildingCity(String msg){ if(msg!=null) logOrPrint(msg); }
	//public void chatCityBuilt(int[] args){}
	
	public void chatBuildingCity(String msg){
		if(msg!=null) logOrPrint(msg);
		if(CityBuiltMessage && mc.thePlayer!=null){
			mc.thePlayer.addChatMessage("** Building city... **");
		}
	}
	
	public void chatCityBuilt(int[] args){
		if(!CityBuiltMessage) return;
		
		if(mc.thePlayer==null){
			citiesBuiltMessages.add(args);
		}else{
			String dirStr="";
			int dI=args[0] - (int)mc.thePlayer.posX;
			int dK=args[2] - (int)mc.thePlayer.posZ;
			if(dI*dI+dK*dK < args[4]*args[4]){
				dirStr="nearby";
			}
			dirStr="to the ";
			if(Math.abs(dI)>2*Math.abs(dK)) dirStr+= dI>0 ? "south" : "north";
			else if(Math.abs(dK)>2*Math.abs(dI)) dirStr+= dK>0 ? "west" : "east";
			else dirStr+= dI > 0 ? (dK>0 ? "southwest" : "southeast") : (dK>0 ? "northwest" : "northeast");

			mc.thePlayer.addChatMessage("** Built city "+dirStr+" ("+args[0]+","+args[1]+","+args[2]+")! **");
		}
	}
	
	


	//****************************  FUNCTION - GenerateSurface  *************************************************************************************//
	//BUKKIT PORT
	//public void populate(World world, Random random, Chunk source){
	//	int chunkI=source.getX(), chunkK=source.getZ();
	public void GenerateSurface( World world, Random random, int i, int k ) {
		if(errFlag) return;
		updateWorldExplored(world);
		chunksExploredFromStart++;

		
		//BUKKIT PORT / MP PORT - Comment out below block
		if(CityBuiltMessage && mc.thePlayer!=null)
			while(citiesBuiltMessages.size()>0) 
				chatCityBuilt(citiesBuiltMessages.remove());

		if(cityStyles.size() > 0 && cityIsSeparated(i,k,CITY_TYPE_WALLED) && random.nextFloat() < ChunkTryProb){
			exploreThreads.add(new WorldGenWalledCity(this, world, random, i, k,TriesPerChunk, ChunkTryProb));
		}
		if(undergroundCityStyles.size() > 0 && cityIsSeparated(i,k,CITY_TYPE_UNDERGROUND) && random.nextFloat() < UndergroundChunkTryProb){
			WorldGeneratorThread wgt=new WorldGenUndergroundCity(this, world, random, i, k,1, UndergroundChunkTryProb);
			int j=Building.findSurfaceJ(world,i,k,127,false,false)- WorldGenUndergroundCity.MAX_DIAM/2 - 5;
			wgt.setSpawnHeight(j-WorldGenUndergroundCity.MAX_DIAM, j, false);
			exploreThreads.add(wgt);
		}
		
		flushGenThreads();
	}
	
	public void GenerateNether( World world, Random random, int chunkI, int chunkK ) {
		GenerateSurface(world,random,chunkI,chunkK);
	}
	
	//****************************  FUNCTION - OnTickInGame  *************************************************************************************//
	//MP Port
	//public boolean OnTickInGame() {
	@Override
	public boolean OnTickInGame(float tick, net.minecraft.client.Minecraft game) {
		//if(exploreThreads.size()==0) doQueuedLighting();
		flushGenThreads();
		runWorldGenThreads();	
		return true;
	}
	

	
	//****************************  FUNCTION - getGlobalOptions  *************************************************************************************//
	public void getGlobalOptions() {
		if(SETTINGS_FILE.exists()){
			BufferedReader br = null;
			try{
				br=new BufferedReader( new FileReader(SETTINGS_FILE) );
				String read = br.readLine();  
				lw.println("Getting global options...");    
		
				while( read != null ) {
		
					//outer wall parameters
					if(read.startsWith( "ChunkTryProb" )) ChunkTryProb = WallStyle.readFloatParam(lw,ChunkTryProb,":",read);
					if(read.startsWith( "ChunkUndergroundTryProb" )) UndergroundChunkTryProb = WallStyle.readFloatParam(lw,UndergroundChunkTryProb,":",read);
					if(read.startsWith( "TriesPerChunk" )) TriesPerChunk = WallStyle.readIntParam(lw,TriesPerChunk,":",read);
					if(read.startsWith( "MinCitySeparation" )) MinCitySeparation= WallStyle.readIntParam(lw,MinCitySeparation,":",read);
					if(read.startsWith( "MinUndergroundCitySeparation" )) UndergroundMinCitySeparation= WallStyle.readIntParam(lw,UndergroundMinCitySeparation,":",read);
		
					if(read.startsWith( "Smooth1" )) Smooth1 = WallStyle.readIntParam(lw,Smooth1,":",read);
					if(read.startsWith( "Smooth2" )) Smooth2 = WallStyle.readIntParam(lw,Smooth2,":",read);
					if(read.startsWith( "Backtrack" )) Backtrack = WallStyle.readIntParam(lw,Backtrack,":",read);
					if(read.startsWith( "CityBuiltMessage" )) CityBuiltMessage = WallStyle.readIntParam(lw,1,":",read)==1;
					
					readChestItemsList(lw,read,br);
		
					read = br.readLine();
				}
				if(TriesPerChunk > MAX_TRIES_PER_CHUNK) TriesPerChunk = MAX_TRIES_PER_CHUNK;
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }
		}else{
			copyDefaultChestItems();
			PrintWriter pw=null;
			try{
				pw=new PrintWriter( new BufferedWriter( new FileWriter(SETTINGS_FILE) ) );
				pw.println("<-README: put this file in the main minecraft folder->");
				pw.println();
				pw.println("<-ChunkTryProb Controls how likely walls are to appear. Should be between 0.0 and 1.0. Lower to make less common->");
				pw.println("<-TriesPerChunk allows multiple attempts per chunk. Only change from 1 if you want very dense walls!->");
				pw.println("<-MinCitySeparation defines a minimum allowable separation between city spawns.->");
				pw.println("ChunkTryProb:"+ChunkTryProb);
				pw.println("ChunkUndergroundTryProb:"+UndergroundChunkTryProb);
				pw.println("MinCitySeparation:"+MinCitySeparation);
				pw.println("MinUndergroundCitySeparation:"+UndergroundMinCitySeparation);
				pw.println("CityBuiltMessage:"+(CityBuiltMessage ? 1:0));
				pw.println();
				pw.println("<Pathfinding>");
				pw.println("<-Smooth1 and Smooth2 control concave and convex smoothing respectively.>");
				pw.println("<-Backtrack - length of backtracking if a dead end is hit>");
				pw.println("<-CurveBias - strength of the bias towards curvier walls. Should be greater than 0.0. >");
				pw.println("<-LengthBiasNorm - wall length at which there is no penalty for generation>");
				pw.println("Smooth1:"+Smooth1);
				pw.println("Smooth2:"+Smooth2);
				pw.println("Backtrack:"+Backtrack);
				pw.println();
				pw.println();
				printDefaultChestItems(pw);
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close(); }
		}
	}


}



