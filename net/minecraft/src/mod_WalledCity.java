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
 * mod_WalledCity is the main class that hooks into ModLoader for the Walled City Mod.
 * It reads the globalSettings file, keeps track of city locations, and runs WorldGenWalledCitys and WorldGenUndergroundCities.
 */

import java.util.Random;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

//BUKKIT PORT
//public class mod_WalledCity extends BlockPopulator implements IBuildingExplorationHandler

public class mod_WalledCity extends BuildingExplorationHandler
{
	public final static int MIN_CITY_LENGTH=40;
	private final static int MAX_EXPLORATION_DISTANCE=30;
	private final static int MAX_FOG_HEIGHT=27;
	public final static int CITY_TYPE_SURFACE=0, CITY_TYPE_NETHER=2, CITY_TYPE_UNDERGROUND=3;
	private final static String SETTINGS_FILE_NAME="WalledCitySettings.txt",
								LOG_FILE_NAME="walled_city_log.txt",
								CITY_TEMPLATES_FOLDER_NAME="walledcity",
								STREET_TEMPLATES_FOLDER_NAME="streets";

	//USER MODIFIABLE PARAMETERS, values here are defaults
	public float GlobalFrequency=0.05F, UndergroundGlobalFrequency=0.005F;
	public int TriesPerChunk=1;
	public int MinCitySeparation=500, UndergroundMinCitySeparation=250;
	public boolean CityBuiltMessage=true;
	public int ConcaveSmoothingScale=10, ConvexSmoothingScale=20, BacktrackLength=9;

	//DATA VARIABLES
	public ArrayList<TemplateWall> cityStyles=null, undergroundCityStyles=new ArrayList<TemplateWall>();
	//private long explrWorldCode;
	public ArrayList<int[]> cityLocations;
	//, undergroundCityLocations;
	
	//private HashMap<Long,ArrayList<int[]> > worldCityLocationsMap=new HashMap<Long,ArrayList<int[]> >(),
	//										undergroundWorldCityLocationsMap=new HashMap<Long,ArrayList<int[]> >();
	
	public LinkedList<int[]> citiesBuiltMessages=new LinkedList<int[]>();
	
	private File cityLocationsSaveFile;

	//****************************  CONSTRUCTOR - mod_WalledCity  *************************************************************************************//
	public mod_WalledCity() {
		ModLoader.SetInGameHook(this,true,true);
		loadingMessage="Generating cities";
		max_exploration_distance=MAX_EXPLORATION_DISTANCE;
		
		//MP PORT - uncomment
		//loadDataFiles();
		//master=this;
	}
	
	@Override
	public String toString(){
		return WALLED_CITY_MOD_STRING;
	}
	
	//****************************  FUNCTION - loadDataFiles *************************************************************************************//
	public void loadDataFiles(){
		try {
			//read and check values from file
			lw= new PrintWriter( new BufferedWriter( new FileWriter(new File(BASE_DIRECTORY,LOG_FILE_NAME)) ));
			
			logOrPrint("Loading options and templates for the Walled City Generator.");
			getGlobalOptions();
			
			File stylesDirectory=new File(RESOURCES_DIRECTORY,CITY_TEMPLATES_FOLDER_NAME);
			cityStyles=TemplateWall.loadWallStylesFromDir(stylesDirectory,this);
			TemplateWall.loadStreets(cityStyles,new File(stylesDirectory,STREET_TEMPLATES_FOLDER_NAME),this);
			for(int m=0; m<cityStyles.size(); m++){
				if(cityStyles.get(m).underground){
					TemplateWall uws = cityStyles.remove(m);
					uws.streets.add(uws); //underground cities have no outer walls, so this should be a street style
					undergroundCityStyles.add(uws);
					m--;
			}}

			lw.println("\nTemplate loading complete.");
			lw.println("Probability of generation attempt per chunk explored is "+GlobalFrequency+", with "+TriesPerChunk+" tries per chunk.");
			if(GlobalFrequency <0.000001 && UndergroundGlobalFrequency<0.000001) errFlag=true;
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
		//ArrayList<int[]> locations = cityType==CITY_TYPE_WALLED ? cityLocations : undergroundCityLocations;
		if(cityLocations ==null) 
			return true;
		for(int [] location : cityLocations){
			if( location[2]==cityType && Math.abs(location[0]-i) + Math.abs(location[1]-k) 
					                     < (cityType==CITY_TYPE_SURFACE ?  MinCitySeparation : UndergroundMinCitySeparation)){
				return false;
			}
		}
		return true;
	}
	
	//****************************  FUNCTION - saveCityLocations *************************************************************************************//
	public void saveCityLocations(){
		PrintWriter pw=null;
		try{
			pw=new PrintWriter( new BufferedWriter( new FileWriter(cityLocationsSaveFile) ) );
			for(int[] location : cityLocations){
				pw.println(new StringBuilder(Integer.toString(location[0]))
								.append(",").append(Integer.toString(location[1]))
								.append(",").append(Integer.toString(location[2])));
			}
		}catch(IOException e) {System.out.println(e.getMessage()); }
		finally{ if(pw!=null) pw.close(); }
	}

	//****************************  FUNCTION - updateWorldExplored *************************************************************************************//
	public void updateWorldExplored(World world_) {
		if(Building.getWorldCode(world_)!=explrWorldCode){
			setNewWorld(world_,"Starting to survey a world for city generation...");
			
			//kill zombies
			for(WorldGeneratorThread wgt: exploreThreads) killZombie(wgt);
			exploreThreads=new LinkedList<WorldGeneratorThread>();
			
			//clear city locations, read in saved locations if they exist
			cityLocations=new ArrayList<int[]>();
			cityLocationsSaveFile=new File(((SaveHandler)world.func_40479_y()).getSaveDirectory(),"citylocations.txt"); //classy
			if(cityLocationsSaveFile.exists()){
				cityLocations=new ArrayList<int[]>();
				BufferedReader br = null;
				try{
					br=new BufferedReader( new FileReader(cityLocationsSaveFile) );
					for(String read=br.readLine(); read!=null; read=br.readLine()){
						String[] split=read.split(",");
						if(split.length==3){
							cityLocations.add(new int[]{Integer.parseInt(split[0]),Integer.parseInt(split[1]),Integer.parseInt(split[2])});
						}
					}
				}catch(IOException e) {System.out.println(e.getMessage()); }
				finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }
			}
		}
	}
	
	//****************************  FUNCTION - isGeneratorStillValid *************************************************************************************//
	public boolean isGeneratorStillValid(WorldGeneratorThread wgt){
		return cityIsSeparated(wgt.chunkI,wgt.chunkK,wgt.spawn_surface ? CITY_TYPE_SURFACE : CITY_TYPE_UNDERGROUND);
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
			if(Math.abs(dI)>2*Math.abs(dK)) dirStr+= dI>0 ? "east" : "west";
			else if(Math.abs(dK)>2*Math.abs(dI)) dirStr+= dK>0 ? "south" : "north";
			else dirStr+= dI > 0 
							? (dK>0 ? "southeast" : "northeast") 
							: (dK>0 ? "southwest" : "northwest");

			mc.thePlayer.addChatMessage("** Built city "+dirStr+" ("+args[0]+","+args[1]+","+args[2]+")! **");
		}
	}
	
	


	//****************************  FUNCTION - generate *************************************************************************************//
	public void generate( World world, Random random, int i, int k ) {
		//BUKKIT PORT / MP PORT - Comment out below block
		if(CityBuiltMessage && mc.thePlayer!=null)
			while(citiesBuiltMessages.size()>0) 
				chatCityBuilt(citiesBuiltMessages.remove());
		
		if(cityStyles.size() > 0 && cityIsSeparated(i,k,CITY_TYPE_SURFACE) && random.nextFloat() < GlobalFrequency){
			exploreThreads.add(new WorldGenWalledCity(this, world, random, i, k,TriesPerChunk, GlobalFrequency));
		}
		if(undergroundCityStyles.size() > 0 && cityIsSeparated(i,k,CITY_TYPE_UNDERGROUND) && random.nextFloat() < UndergroundGlobalFrequency){
			WorldGeneratorThread wgt=new WorldGenUndergroundCity(this, world, random, i, k,1, UndergroundGlobalFrequency);
			int maxSpawnHeight=Building.findSurfaceJ(world,i,k,world.field_35472_c-1,false,false)- WorldGenUndergroundCity.MAX_DIAM/2 - 5; //44 at sea level
			int minSpawnHeight=MAX_FOG_HEIGHT+WorldGenUndergroundCity.MAX_DIAM/2 - 8; //34, a pretty thin margin. Too thin for underocean cities?
			if(minSpawnHeight<=maxSpawnHeight)
				wgt.setSpawnHeight(minSpawnHeight, maxSpawnHeight, false);
			exploreThreads.add(wgt);
		}
	}
	
	//****************************  FUNCTION - getGlobalOptions  *************************************************************************************//
	public void getGlobalOptions() {
		File settingsFile=new File(BASE_DIRECTORY,SETTINGS_FILE_NAME);
		if(settingsFile.exists()){
			BufferedReader br = null;
			try{
				br=new BufferedReader( new FileReader(settingsFile) );  
				lw.println("Getting global options...");    
		
				for(String read=br.readLine(); read!=null; read=br.readLine()){
		
					//outer wall parameters
					if(read.startsWith( "GlobalFrequency" )) GlobalFrequency = readFloatParam(lw,GlobalFrequency,":",read);
					if(read.startsWith( "UndergroundGlobalFrequency" )) UndergroundGlobalFrequency = readFloatParam(lw,UndergroundGlobalFrequency,":",read);
					if(read.startsWith( "TriesPerChunk" )) TriesPerChunk = readIntParam(lw,TriesPerChunk,":",read);
					if(read.startsWith( "MinCitySeparation" )) MinCitySeparation= readIntParam(lw,MinCitySeparation,":",read);
					if(read.startsWith( "MinUndergroundCitySeparation" )) UndergroundMinCitySeparation= readIntParam(lw,UndergroundMinCitySeparation,":",read);
		
					if(read.startsWith( "ConcaveSmoothingScale" )) ConcaveSmoothingScale = readIntParam(lw,ConcaveSmoothingScale,":",read);
					if(read.startsWith( "ConvexSmoothingScale" )) ConvexSmoothingScale = readIntParam(lw,ConvexSmoothingScale,":",read);
					if(read.startsWith( "BacktrackLength" )) BacktrackLength = readIntParam(lw,BacktrackLength,":",read);
					if(read.startsWith( "CityBuiltMessage" )) CityBuiltMessage = readIntParam(lw,1,":",read)==1;
					
					readChestItemsList(lw,read,br);
		
				}
				if(TriesPerChunk > MAX_TRIES_PER_CHUNK) TriesPerChunk = MAX_TRIES_PER_CHUNK;
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }
		}else{
			copyDefaultChestItems();
			PrintWriter pw=null;
			try{
				pw=new PrintWriter( new BufferedWriter( new FileWriter(settingsFile) ) );
				pw.println("<-README: put this file in the main minecraft folder->");
				pw.println();
				pw.println("<-GlobalFrequency/UndergroundGlobalFrequency controls how likely aboveground/belowground cities are to appear. Should be between 0.0 and 1.0. Lower to make less common->");
				pw.println("<-MinCitySeparation/UndergroundMinCitySeparation define a minimum allowable separation between city spawns.->");
				pw.println("<-CityBuiltMessage controls whether the player receives message when a city is building. Set to 1 to receive message, 0 for no messages.->");
				pw.println("GlobalFrequency:"+GlobalFrequency);
				pw.println("UndergroundGlobalFrequency:"+UndergroundGlobalFrequency);
				pw.println("MinCitySeparation:"+MinCitySeparation);
				pw.println("MinUndergroundCitySeparation:"+UndergroundMinCitySeparation);
				pw.println("CityBuiltMessage:"+(CityBuiltMessage ? 1:0));
				pw.println();
				pw.println("<-Wall Pathfinding->");
				pw.println("<-ConcaveSmoothingScale and ConvexSmoothingScale specifiy the maximum length that can be smoothed away in walls for cocave/convex curves respectively.->");
				pw.println("<-BacktrackLength - length of backtracking for wall planning if a dead end is hit->");
				pw.println("ConcaveSmoothingScale:"+ConcaveSmoothingScale);
				pw.println("ConvexSmoothingScale:"+ConvexSmoothingScale);
				pw.println("BacktrackLength:"+BacktrackLength);
				pw.println();
				pw.println();
				pw.println("<-Chest contents->");
				pw.println("<-Tries is the number of selections that will be made for this chest type.->");
				pw.println("<-Format for items is <itemID>,<selection weight>,<min stack size>,<max stack size> ->");
				pw.println("<-So e.g. 262,1,1,12 means a stack of between 1 and 12 arrows, with a selection weight of 1.->");
				printDefaultChestItems(pw);
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close(); }
		}
	}
	
	private void writeCityLocations(ArrayList<int[]> locations, int worldTypeCode){
		File cityLocationsFile=new File(cityLocationsSaveFile,"citylocations.txt");
		PrintWriter pw=null;
		try{
			pw=new PrintWriter( new BufferedWriter( new FileWriter(cityLocationsFile) ) );
			for(int[] location : locations){
				pw.println(new StringBuilder(Integer.toString(worldTypeCode)).append(",")
								.append(Integer.toString(location[0])).append(",")
								.append(Integer.toString(location[1])).append(","));
			}
		}catch(IOException e) {System.out.println(e.getMessage()); }
		finally{ if(pw!=null) pw.close(); }
	}
	
	
	
}



