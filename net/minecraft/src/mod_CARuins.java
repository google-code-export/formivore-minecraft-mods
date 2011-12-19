package net.minecraft.src;
/*
 *  Source code for the CA Ruins Mod for the game Minecraft
 *  Copyright (C) 2011 by formivore

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * mod_CABuilding is the main class that hooks into ModLoader for the Automata Generator mod.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;


public class mod_CARuins extends BuildingExplorationHandler{
	private final static int MAX_EXPLORATION_DISTANCE=30;
	private final static String AUTOMATA_RULES_STRING="AUTOMATA RULES",LINEAR_STR="linear",SYMMETRIC_STR="symmetric", BOTH_STR="both";
	private final static TemplateRule[] DEFAULT_BLOCK_RULES = new TemplateRule[]{
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),			//Ocean
		new TemplateRule(new int[]{1,98,98},new int[]{0,1,2},100),    		//Plains                
		new TemplateRule(new int[]{24},new int[]{0},100),          			//Desert            
		new TemplateRule(new int[]{1,98,98},new int[]{0,0,2},100),          //Hills             
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),          //Forest            
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),          //Taiga             
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),          //Swampland         
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),          //River             
		new TemplateRule(new int[]{112},new int[]{0,},100),          		//Nether            
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),          //Sky               
		new TemplateRule(new int[]{98,98,98},new int[]{0,1,2},100),          //FrozenOcean       
		new TemplateRule(new int[]{98,98,98},new int[]{0,1,2},100),           //FrozenRiver       
		new TemplateRule(new int[]{98,98,98},new int[]{0,2,2},100),          //IcePlains         
		new TemplateRule(new int[]{98,98,98},new int[]{0,2,2},100),          //IceMountains      
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),        	//MushroomIsland    
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100)};     	//MushroomIslandShore 
	
	//WARNING! Make sure the first DEFAULT_BLOCK_RULES.length biome Strings in Building are the ones we want here.
	private final static String[] BLOCK_RULE_NAMES; 
	static{
		BLOCK_RULE_NAMES=new String[DEFAULT_BLOCK_RULES.length];
		for(int m=0; m<BLOCK_RULE_NAMES.length; m++){
			BLOCK_RULE_NAMES[m]=Building.BIOME_NAMES[m].replaceAll("\\s", "") + "BlockRule";
		}
	}
	
	private final static String[] DEFAULT_BLOCK_RULE_VARIABLE_STRINGS = new String[]{
		"OceanBlockRule",
		"PlainsBlockRule",
		"DesertBlockRule",
		"HillsBlockRule",
		"ForestBlockRule",
		"TaigaBlockRule",
		"SwamplandBlockRule",
		"RiverBlockRule",
		"NetherBlockRule",
		"SkyBlockRule",
		"FrozenOceanBlockRule",
		"FrozenRiverBlockRule",
		"IcePlainsBlockRule",
		"IceMountainsBlockRule",
		"MushroomIslandBlockRule",
		"MushroomIslandShoreBlockRule"};
	
	private final static String SETTINGS_FILE_NAME="CARuinsSettings.txt",
								LOG_FILE_NAME="caruins_log.txt";
	
	public float GlobalFrequency=0.01F;
	public int TriesPerChunk=1;
	public int MinHeight=20,MaxHeight=70;
	public int ContainerWidth=40, ContainerLength=40;
	public int linearChance=50;
	public float SymmetricSeedDensity=0.5F;
	public int SymmetricSeedMaxWidth=8;
	public int MinHeightBeforeOscillation=12;
	public boolean SmoothWithStairs=true, MakeFloors=true;
	
	public TemplateRule[] blockRules=new TemplateRule[DEFAULT_BLOCK_RULES.length];
	
	ArrayList<byte[][]> caRules=null;
	
	//****************************  CONSTRUCTOR - mod_GreatWall*************************************************************************************//
	public mod_CARuins() {	
		ModLoader.SetInGameHook(this,true,true);
		loadingMessage="Running automata";
		max_exploration_distance=MAX_EXPLORATION_DISTANCE;
		
		
		for(int m=0; m<DEFAULT_BLOCK_RULES .length; m++){
			blockRules[m]=DEFAULT_BLOCK_RULES[m];
		}
		
		//MP PORT - uncomment
		//loadDataFiles();
		//master=this;
	}
	
	//****************************   FUNCTION - updateWorldExplored *************************************************************************************//
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
	
	//****************************  FUNCTION - loadDataFiles *************************************************************************************//
	public void loadDataFiles(){
		try {
			//read and check values from file
			lw= new PrintWriter( new BufferedWriter( new FileWriter(new File(BASE_DIRECTORY,LOG_FILE_NAME)) ));
			
			//for(int[] rule : BuildingCellularAutomaton.DEFAULT_BIN_CA_RULES)
			//	BuildingCellularAutomaton.intCodeToStr(rule);
			
			logOrPrint("Loading options and for the Cellular Automata Generator");
			getGlobalOptions();

			lw.println("Probability of generation attempt per chunk explored is "+GlobalFrequency+", with "+TriesPerChunk+" tries per chunk.");
			if(GlobalFrequency <0.000001 || caRules==null || caRules.size()==0) errFlag=true;
		} catch( Exception e ) {
			errFlag=true;
			logOrPrint( "There was a problem loading the Cellular Automata Generator: "+ e.getMessage() );
			lw.println( "There was a problem loading the Cellular Automata Generator: "+ e.getMessage() );
			e.printStackTrace();
		}finally{ if(lw!=null) lw.close(); }
		dataFilesLoaded=true;
	}
	
	//****************************  FUNCTION - getGlobalOptions *************************************************************************************//
	private void getGlobalOptions(){
		
		copyDefaultChestItems();
		
		File settingsFile=new File(BASE_DIRECTORY,SETTINGS_FILE_NAME);
		caRules=new ArrayList<byte[][]>();
		if(settingsFile.exists()){
			BufferedReader br = null;
			//ArrayList<int[]> digitalCARules=new ArrayList<int[]>();
			String seedType="", zeroNeighborsStr="";
			try{
				br=new BufferedReader( new FileReader(settingsFile) );
				lw.println("Getting global options...");    
	
				for(String read=br.readLine(); read!=null; read=br.readLine()){
					if(read.startsWith( "GlobalFrequency" )) GlobalFrequency = readFloatParam(lw,GlobalFrequency,":",read);
					if(read.startsWith( "TriesPerChunk" )) TriesPerChunk = readIntParam(lw,TriesPerChunk,":",read);
					if(read.startsWith( "MinHeight" )) MinHeight = readIntParam(lw,MinHeight,":",read);
					if(read.startsWith( "MaxHeight" )) MaxHeight = readIntParam(lw,MaxHeight,":",read);
					if(read.startsWith( "MinHeightBeforeOscillation" )) MinHeightBeforeOscillation = readIntParam(lw,MinHeightBeforeOscillation,":",read);
					if(read.startsWith( "SmoothWithStairs" )) SmoothWithStairs = readIntParam(lw,1,":",read)==1;
					if(read.startsWith( "MakeFloors" )) MakeFloors = readIntParam(lw,1,":",read)==1;
					if(read.startsWith( "ContainerWidth" )) ContainerWidth = readIntParam(lw,ContainerWidth,":",read);
					if(read.startsWith( "ContainerLength" )) ContainerLength = readIntParam(lw,ContainerLength,":",read);
					
					if(read.startsWith( "SeedType" )) seedType=read.split(":")[1].trim();
					if(read.startsWith( "SymmetricSeedDensity" )) SymmetricSeedDensity = readFloatParam(lw,SymmetricSeedDensity,":",read);
					if(read.startsWith( "SymmetricSeedMaxWidth" )) SymmetricSeedMaxWidth = readIntParam(lw,SymmetricSeedMaxWidth,":",read);
					
					
					
					for(int m=0; m<DEFAULT_BLOCK_RULES.length; m++){
						try{ 
							if(read.startsWith(BLOCK_RULE_NAMES[m])) 
								blockRules[m]=readRuleIdOrRule(":",read,null); 
						}catch(Exception e ){  
							blockRules[m]=DEFAULT_BLOCK_RULES[m]; 
							lw.println(e.getMessage());
						}
					}
					
					if(read.startsWith(AUTOMATA_RULES_STRING)){
						for(read=br.readLine(); read!= null; read=br.readLine()){
							if(read.startsWith("B") || read.startsWith("b")) 
								caRules.add(BuildingCellularAutomaton.parseCARule(read.split("#")[0],lw));
						}
						break;
					}
				}
				
				if(TriesPerChunk > MAX_TRIES_PER_CHUNK) TriesPerChunk = MAX_TRIES_PER_CHUNK;
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }
			
			
			if(seedType.toUpperCase().equals(LINEAR_STR.toUpperCase())) linearChance=100;
			else if(seedType.toUpperCase().equals(SYMMETRIC_STR.toUpperCase())) linearChance=0;
			else linearChance=50;
		}
		else{
			PrintWriter pw=null;
			try{
				pw=new PrintWriter( new BufferedWriter( new FileWriter(settingsFile) ) );
				pw.println("<-README: put this file in the main minecraft folder, e.g. C:\\Users\\Administrator\\AppData\\Roaming\\.minecraft\\->");
				pw.println();
				pw.println("<-GlobalFrequency controls how likely structures are to appear. Should be between 0.0 and 1.0. Lower to make less common->");
				pw.println("<-TriesPerChunk allows multiple attempts per chunk. Only change from 1 if you want very dense generation!->");
				pw.println("GlobalFrequency:"+GlobalFrequency);
				pw.println("TriesPerChunk:"+TriesPerChunk);
				pw.println();
				pw.println("<-MinHeight and MaxHeight are the minimum and maximum allowed height of the structures->");
				pw.println("<-MinHeightBeforeOscillation - Any structures that form oscillators before MaxOscillatorCullStep will be culled.->");
				pw.println("<-Smooth with stairs - If set to 1, will smooth out ruins by placing extra stair blocks.->");
				pw.println("<-ContainerWidth and ContainerLength are the dimensions of the bounding rectangle.->");
				pw.println("MinHeight:"+MinHeight);
				pw.println("MaxHeight:"+MaxHeight);
				pw.println("MinHeightBeforeOscillation:"+MinHeightBeforeOscillation);
				pw.println("SmoothWithStairs:"+(SmoothWithStairs? 1:0));
				pw.println("MakeFloors:"+(MakeFloors? 1:0));
				pw.println("ContainerWidth:"+ContainerWidth);
				pw.println("ContainerLength:"+ContainerLength);
				pw.println();
				pw.println("<-Seed type is the type of seed used. Enter one of: linear, symmetric, or both.->");
				pw.println("<-SymmetricSeedDensity is the density (out of 1.0) of live blocks in the symmetric seed.->");
				pw.println("<-SymmetricSeedMaxWidth is the maximum width of symmetric seeds->");
				pw.println("<-BlockRule is the template rule that controls what blocks the structure will be made out of.->");
				pw.println("SeedType:"+(linearChance==100 ? LINEAR_STR: linearChance==0 ? SYMMETRIC_STR:BOTH_STR));
				pw.println("SymmetricSeedDensity:"+SymmetricSeedDensity);
				pw.println("SymmetricSeedMaxWidth:"+SymmetricSeedMaxWidth);
				pw.println();
				for(int m=0; m<DEFAULT_BLOCK_RULES.length; m++){
					pw.println(BLOCK_RULE_NAMES[m]+":"+DEFAULT_BLOCK_RULES[m].toString());
				}
				pw.println();
				pw.println();
				pw.println();
				pw.println("<-An automata rule should be in the form B<neighbor digits>/S<neighbor digits>, where B stands for \"birth\" and S stands->");
				pw.println("<-   for \"survive\". <neighbor digits> are the subset the digits from 0 to 8 on which the rule will birth or survive.->");
				pw.println("<-   For example, the Game of Life has the rule code B3/S23.->");
				pw.println(AUTOMATA_RULES_STRING);
				for(String[] defaultRule : DEFAULT_CA_RULES){
					pw.println(defaultRule[0] + (defaultRule[1].length()>0 ? ("  #"+defaultRule[1]) : ""));
					caRules.add(BuildingCellularAutomaton.parseCARule(defaultRule[0],lw));
				}
			}
			catch(Exception e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close(); }
		}
	}
	
	//****************************  FUNCTION - generate *************************************************************************************//
	@Override
	public void generate( World world, Random random, int i, int k ) {
		if(random.nextFloat() < GlobalFrequency){
			exploreThreads.add(new WorldGeneratorThread(master, world, random, i, k,TriesPerChunk, GlobalFrequency){
					public boolean generate(int i0, int j0, int k0) throws InterruptedException{
						setName("WorldGenAutomata");
						chestTries=master.chestTries;
						chestItems=master.chestItems;
						int th=MinHeight+random.nextInt(MaxHeight-MinHeight+1);
						byte[][] caRule=caRules.get(random.nextInt(caRules.size()));
						byte[][] seed = random.nextInt(100) < linearChance
							? BuildingCellularAutomaton.makeLinearSeed(ContainerWidth,random)
							: BuildingCellularAutomaton.makeSymmetricSeed(SymmetricSeedMaxWidth,SymmetricSeedMaxWidth,SymmetricSeedDensity,random);
						
						TemplateRule blockRule=blockRules[Building.getBiomeNum(world.getWorldChunkManager().getBiomeGenAt(i0,k0))];
						
						BuildingCellularAutomaton bca=new BuildingCellularAutomaton(this,blockRule,random.nextInt(4),1, false, 
								                           ContainerWidth, th,ContainerLength,seed,caRule,new int[]{i0,j0,k0});
						if(bca.plan(true,MinHeightBeforeOscillation) && bca.queryCanBuild(0,true)){
							bca.build(SmoothWithStairs,MakeFloors);
							return true;
						}
						return false;
					}
				}

			);
		}
	}
	
	public final static String[][] DEFAULT_CA_RULES=new String[][]{
		{"B36/S013468","(110,74)"},
		{"B36/S013468",""},
		{"B36/S013468",""},
		{"B38/S023468","(169,138)"},
		{"B38/S023468",""},
		{"B38/S023468",""},
		{"B368/S245","Morley"},
		{"B368/S245",""},
		{"B3/S23","Life - good for weird temples"},
		{"B3/S23",""},
		{"B3/S23",""},
		{"B36/S125","2x2 - pillar & arch temple/tower/statue"},
		{"B36/S125",""},
		{"B36/S23","High Life - space invaders"},
		{"B36/S23",""},
		//{"B3/S012345678","Inkspots - nice towers"},
		//{"B3/S012345678",""},
		{"B45/S2345","45-rule - square towers"},
		{"B45/S2345",""},
		{"B2/S01","\"temple\""},
		{"B35678/S015678","legged amoeba"},
		{"B35678/S015678",""},
		{"B35678/S015678",""}
	};
}
