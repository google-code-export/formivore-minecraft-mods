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
 * mod_GreatWall is the main class that hooks into ModLoader for the Great Wall Mod.
 * It reads the globalSettings file and runs WorldGenWalledCities.
 */

import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.*;


//BUKKIT PORT
//public class mod_GreatWall extends BlockPopulator implements IBuildingExplorationHandler 

public class mod_GreatWall extends BuildingExplorationHandler
{
	private final static int MAX_EXPLORATION_DISTANCE=30;
	public final static float ACCEPT_ALPHA=50.0F;
	private final static String SETTINGS_FILE_NAME="GreatWallSettings.txt",
								LOG_FILE_NAME="great_wall_log.txt",
								CITY_TEMPLATES_FOLDER_NAME="greatwall";

	//USER MODIFIABLE PARAMETERS, values below are defaults
	public float GlobalFrequency=0.0015F;
	public int TriesPerChunk=1;
	public float CurveBias=0.0F;
	public int LengthBiasNorm=200;
	public int ConcaveSmoothingScale=10, ConvexSmoothingScale=20, BacktrackLength=9;

	//DATA VARIABLES
	public ArrayList<TemplateWall> wallStyles=null;
	public int[] placedCoords=null;
	public World placedWorld=null;
	//public final Block surveyorsRod= new BlockSurveyorsRod(131, 0,this).setHardness(2.0F).setResistance(5.0F).setBlockName("SureveyorsRod");

	//****************************  CONSTRUCTOR - mod_GreatWall*************************************************************************************//
	public mod_GreatWall() {
		//ModLoader.RegisterBlock(surveyorsRod);
	    //magicWall.blockIndexInTexture = ModLoader.addOverride("/terrain.png", "/texturetest.png");
			
		//ModLoader.AddRecipe(new ItemStack(surveyorsRod,8), new Object[]{ "##", "##", Character.valueOf('#'), Block.dirt});
		
		ModLoader.SetInGameHook(this,true,true);
		loadingMessage="Generating walls";
		max_exploration_distance=MAX_EXPLORATION_DISTANCE;
		
		//MP PORT - uncomment
		//loadDataFiles();
	}
	
	@Override
	public String toString(){
		return GREAT_WALL_MOD_STRING;
	}
	
	//****************************  FUNCTION - loadDataFiles *************************************************************************************//
	public void loadDataFiles(){
		try {
			//read and check values from file
			lw= new PrintWriter( new BufferedWriter( new FileWriter(new File(BASE_DIRECTORY,LOG_FILE_NAME)) ));
			logOrPrint("Loading options and templates for the Great Wall Mod.");
			getGlobalOptions();
			
			File stylesDirectory=new File(new File(BASE_DIRECTORY,RESOURCES_FOLDER_NAME),CITY_TEMPLATES_FOLDER_NAME);
			wallStyles=TemplateWall.loadWallStylesFromDir(stylesDirectory,this);

			lw.println("\nTemplate loading complete.");
			lw.println("Probability of generation attempt per chunk explored is "+GlobalFrequency+", with "+TriesPerChunk+" tries per chunk.");
			if(GlobalFrequency <0.000001) errFlag=true;
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
			setNewWorld(world_,"Starting to survey a world for wall generation...");
			
			if(this==master){
				//kill zombies
				for(WorldGeneratorThread wgt: exploreThreads) killZombie(wgt);
				exploreThreads=new LinkedList<WorldGeneratorThread>();
			} else {
				master.updateWorldExplored(world);
				exploreThreads=master.exploreThreads;
			}
		}
	}
	
	//****************************  FUNCTION - generate *************************************************************************************//
	@Override
	public void generate( World world, Random random, int i, int k ) {
		if(random.nextFloat() < GlobalFrequency)
			exploreThreads.add(new WorldGenGreatWall(this,master,world, random, i, k,TriesPerChunk, GlobalFrequency));
	}

	//****************************  FUNCTION - getGlobalOptions  *************************************************************************************//
	private void getGlobalOptions(){
		File settingsFile=new File(BASE_DIRECTORY,SETTINGS_FILE_NAME);
		if(settingsFile.exists()){
			BufferedReader br = null;
			try{
				br=new BufferedReader( new FileReader(settingsFile) );
				String read = br.readLine();
				lw.println("Getting global options...");    
	
				while( read != null ) {
					if(read.startsWith( "GlobalFrequency" )) GlobalFrequency = readFloatParam(lw,GlobalFrequency,":",read);
					if(read.startsWith( "TriesPerChunk" )) TriesPerChunk = readIntParam(lw,TriesPerChunk,":",read);
					if(read.startsWith( "CurveBias" )) CurveBias = readFloatParam(lw,CurveBias,":",read);
					if(read.startsWith( "LengthBiasNorm" )) LengthBiasNorm = readIntParam(lw,LengthBiasNorm,":",read);
					if(read.startsWith( "ConcaveSmoothingScale" )) ConcaveSmoothingScale = readIntParam(lw,ConcaveSmoothingScale,":",read);
					if(read.startsWith( "ConvexSmoothingScale" )) ConvexSmoothingScale = readIntParam(lw,ConvexSmoothingScale,":",read);
					if(read.startsWith( "BacktrackLength" )) BacktrackLength = readIntParam(lw,BacktrackLength,":",read);
					
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
				pw=new PrintWriter( new BufferedWriter( new FileWriter(settingsFile) ) );
				pw.println("<-README: put this file in the main minecraft folder, e.g. C:\\Users\\Administrator\\AppData\\Roaming\\.minecraft\\->");
				pw.println();
				pw.println("<-GlobalFrequency controls how likely walls are to appear. Should be between 0.0 and 1.0. Lower to make less common->");
				pw.println("<-TriesPerChunk allows multiple attempts per chunk. Only change from 1 if you want very dense walls!->");
				pw.println("GlobalFrequency:"+GlobalFrequency);
				pw.println("TriesPerChunk:"+TriesPerChunk);
				pw.println();
				pw.println("<-Wall Pathfinding->");
				pw.println("<-ConcaveSmoothingScale and ConvexSmoothingScale specifiy the maximum length that can be smoothed away in walls for cocave/convex curves respectively.->");
				pw.println("<-BacktrackLength - length of backtracking for wall planning if a dead end is hit->");
				pw.println("<-CurveBias - strength of the bias towards curvier walls. Value should be between 0.0 and 1.0.->");
				pw.println("<-LengthBiasNorm - wall length at which there is no penalty for generation>");
				pw.println("ConcaveSmoothingScale:"+ConcaveSmoothingScale);
				pw.println("ConvexSmoothingScale:"+ConvexSmoothingScale);
				pw.println("BacktrackLength:"+BacktrackLength);
				pw.println("CurveBias:"+CurveBias);
				pw.println("LengthBiasNorm:"+LengthBiasNorm);
				pw.println();
				pw.println();
				pw.println("<-Chest contents->");
				pw.println("<-Tries is the number of selections that will be made for this chest type.->");
				pw.println("<-Format for items is <itemID>,<selection weight>,<min stack size>,<max stack size> ->");
				pw.println("<-So e.g. 262,1,1,12 means a stack of between 1 and 12 arrows, with a selection weight of 1.->");
				printDefaultChestItems(pw);
			}
			catch(Exception e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close();}

		}

	}

}
