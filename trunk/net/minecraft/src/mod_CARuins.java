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
	
	public final static String[] BIOME_NAMES={
		"Underground",
		"Ocean",
		"Plains",
		"Desert",
		"Hills",
		"Forest",
		"Taiga",
		"Swampland",
		"River",
		"Hell",
		"Sky",
		"Ice Plains",
		"Ice Mountains",
		"Mushroom Island",
		"Beach"
		};
	
	private final static TemplateRule[] DEFAULT_BLOCK_RULES = new TemplateRule[]{
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),			//Underground, unused
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
		new TemplateRule(new int[]{98,98,98},new int[]{0,2,2},100),         //IcePlains         
		new TemplateRule(new int[]{98,98,98},new int[]{0,2,2},100),         //IceMountains      
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100),        	//MushroomIsland    
		new TemplateRule(new int[]{4,48,48},new int[]{0,0,0},100)     	    //Beach
	};
	
	//WARNING! Make sure the first DEFAULT_BLOCK_RULES.length biome Strings in Building are the ones we want here.
	private final static String[] BLOCK_RULE_NAMES; 
	static{
		BLOCK_RULE_NAMES=new String[DEFAULT_BLOCK_RULES.length];
		for(int m=0; m<BLOCK_RULE_NAMES.length; m++){
			BLOCK_RULE_NAMES[m]=Building.BIOME_NAMES[m].replaceAll("\\s", "") + "BlockRule";
		}
	}
	
	private final static String SETTINGS_FILE_NAME="CARuinsSettings.txt",
								LOG_FILE_NAME="caruins_log.txt";
	
	public final static String[] SEED_TYPE_STRINGS=new String[]{"SymmetricSeedWeight","LinearSeedWeight","CircularSeedWeight","CruciformSeedWeight"};
	public final static int[] SEED_TYPE_CODES=new int[]{0,1,2,3};
	public int[] seedTypeWeights=new int[]{8,2,2,1};
	
	public float GlobalFrequency=0.01F;
	public int TriesPerChunk=1;
	public int MinHeight=20,MaxHeight=70;
	public int ContainerWidth=40, ContainerLength=40;
	public float SymmetricSeedDensity=0.5F;
	public int MinHeightBeforeOscillation=12;
	public boolean SmoothWithStairs=true, MakeFloors=true;
	
	public TemplateRule[] blockRules=new TemplateRule[DEFAULT_BLOCK_RULES.length];
	
	ArrayList<byte[][]> caRules=null;
	int[][] caRulesWeightsAndIndex=null;
	
	//byte[][] fixedRule;
	
	//****************************  CONSTRUCTOR - mod_GreatWall*************************************************************************************//
	public mod_CARuins() {	
		ModLoader.setInGameHook(this,true,true);
		loadingMessage="Running automata";
		max_exploration_distance=MAX_EXPLORATION_DISTANCE;
		
		
		for(int m=0; m<DEFAULT_BLOCK_RULES .length; m++){
			blockRules[m]=DEFAULT_BLOCK_RULES[m];
		}
		
		/*
		Random rnd=new Random();
		int a=rnd.nextInt(2)+2, b=rnd.nextInt(2)+2;
		String ruleStr="B4";
		for(int m=5;m<=8;m++) if(rnd.nextInt(3)!=0)  ruleStr+=m;
		ruleStr+="/S";
		for(int m=0;m<=8;m++) if(rnd.nextInt(3)!=0)  ruleStr+=m;
		fixedRule=BuildingCellularAutomaton.parseCARule(ruleStr, null);
		System.out.println("Using fixed rule "+ruleStr);
		*/
		
		//MP PORT - uncomment
		//loadDataFiles();
		//master=this;
	}
	
	//****************************   FUNCTION - updateWorldExplored *************************************************************************************//
	public synchronized void updateWorldExplored(World world_) {
		if(Building.getWorldCode(world_)!=explrWorldCode){
			setNewWorld(world_,"Starting to survey a world for automata generation...");
			
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
		ArrayList<Integer> caRuleWeights=new ArrayList<Integer>();
		if(settingsFile.exists()){
			BufferedReader br = null;
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
					if(read.startsWith( "SymmetricSeedDensity" )) SymmetricSeedDensity = readFloatParam(lw,SymmetricSeedDensity,":",read);
					
					for(int m=0; m<SEED_TYPE_STRINGS.length; m++){
						if(read.startsWith(SEED_TYPE_STRINGS[m] )) seedTypeWeights[m] = readIntParam(lw,seedTypeWeights[m],":",read);
					}
					
					for(int m=Building.NATURAL_BIOMES_START; m<DEFAULT_BLOCK_RULES.length; m++){
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
							if(read.startsWith("B") || read.startsWith("b")){
								String[] splitStr=read.split(",");
								caRules.add(BuildingCellularAutomaton.parseCARule(splitStr[0],lw));
								caRuleWeights.add(readIntParam(lw,1,"=",splitStr[1].trim()));
							}
						}
						break;
					}
				}
				
				if(TriesPerChunk > MAX_TRIES_PER_CHUNK) TriesPerChunk = MAX_TRIES_PER_CHUNK;
			}catch(IOException e) { lw.println(e.getMessage()); }
			finally{ try{ if(br!=null) br.close();} catch(IOException e) {} }

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
				pw.println("<-Seed type weights are the relative likelihood weights that different seeds will be used. Weights are nonnegative integers.->");
				pw.println("<-SymmetricSeedDensity is the density (out of 1.0) of live blocks in the symmetric seed.->");
				pw.println("SymmetricSeedDensity:"+SymmetricSeedDensity);
				for(int m=0; m<SEED_TYPE_STRINGS.length; m++){
					pw.println(SEED_TYPE_STRINGS[m]+":"+seedTypeWeights[m]);
				}
				
				
				pw.println();
				pw.println("<-BlockRule is the template rule that controls what blocks the structure will be made out of.->");
				for(int m=Building.NATURAL_BIOMES_START; m<DEFAULT_BLOCK_RULES.length; m++){
					pw.println(BLOCK_RULE_NAMES[m]+":"+DEFAULT_BLOCK_RULES[m].toString());
				}
				pw.println();
				pw.println();
				pw.println();
				pw.println("<-An automata rule should be in the form B<neighbor digits>/S<neighbor digits>, where B stands for \"birth\" and S stands->");
				pw.println("<-   for \"survive\". <neighbor digits> are the subset the digits from 0 to 8 on which the rule will birth or survive.->");
				pw.println("<-   For example, the Game of Life has the rule code B3/S23.->");
				pw.println("<-Rule weights are the relative likelihood weights that different rules will be used. Weights are nonnegative integers.->");
				pw.println(AUTOMATA_RULES_STRING);
				for(String[] defaultRule : DEFAULT_CA_RULES){
					pw.println(defaultRule[0] + ", weight="+defaultRule[1]+(defaultRule[2].length()>0 ? (",  <-"+defaultRule[2])+"->" : ""));
					caRules.add(BuildingCellularAutomaton.parseCARule(defaultRule[0],lw));
					caRuleWeights.add(Integer.parseInt(defaultRule[1]));
				}
			}
			catch(Exception e) { lw.println(e.getMessage()); }
			finally{ if(pw!=null) pw.close(); }
		}
		
		caRulesWeightsAndIndex=new int[2][caRuleWeights.size()];
		for(int m=0; m<caRuleWeights.size(); m++){
			caRulesWeightsAndIndex[0][m]=caRuleWeights.get(m);
			caRulesWeightsAndIndex[1][m]=m;
		}
	}
	
	//****************************  FUNCTION - generate *************************************************************************************//
	@Override
	public void generate( World world, Random random, int i, int k ) {
		if(random.nextFloat() < GlobalFrequency){
			exploreThreads.add(new WorldGeneratorThread(master, world, random, i, k,TriesPerChunk, GlobalFrequency){
					byte[][] caRule=null;
				
					public boolean generate(int i0, int j0, int k0) throws InterruptedException{
						setName("WorldGenAutomata");
						chestTries=master.chestTries;
						chestItems=master.chestItems;
						int th=MinHeight+random.nextInt(MaxHeight-MinHeight+1);
						
						
						if(caRule==null) //if we haven't picked in an earlier generate call 
							caRule=caRules.get(Building.pickWeightedOption(random, caRulesWeightsAndIndex[0], caRulesWeightsAndIndex[1]));
						if(caRule==null) return false;
						
						int seedCode=Building.pickWeightedOption(random, seedTypeWeights, SEED_TYPE_CODES);
						byte[][] seed = seedCode==0 || (caRule[0][0]==0 && caRule[0][1]==0 && caRule[0][2]==0 && caRule[0][3]==0) //only use symmetric for 4-rules
										  			? BuildingCellularAutomaton.makeSymmetricSeed(Math.min(ContainerWidth,ContainerLength),SymmetricSeedDensity,random)
									  : seedCode==1 ? BuildingCellularAutomaton.makeLinearSeed(ContainerWidth,random)
									  : seedCode==2 ? BuildingCellularAutomaton.makeCircularSeed(Math.min(ContainerWidth,ContainerLength),random)
									  : 			  BuildingCellularAutomaton.makeCruciformSeed(Math.min(ContainerWidth,ContainerLength),random);
						
						TemplateRule blockRule=blockRules[Building.getBiomeNum(world.getWorldChunkManager().getBiomeGenAt(i0,k0))];
						
						//can use this to test out new Building classes
						/*
						BuildingSpiralStaircase bss=new BuildingSpiralStaircase(this,blockRule,random.nextInt(4),2*random.nextInt(2)-1,false,-(random.nextInt(10)+1),new int[]{i0,j0,k0});
						bss.build(0,0);
						bss.bottomIsFloor();
						return true;
						*/

						BuildingCellularAutomaton bca=new BuildingCellularAutomaton(this,blockRule,random.nextInt(4),1, false, 
								                           ContainerWidth, th,ContainerLength,seed,caRule,null,new int[]{i0,j0,k0});
						if(bca.plan(true,MinHeightBeforeOscillation) && bca.queryCanBuild(0,true)){
							bca.build(SmoothWithStairs,MakeFloors);
							
							
							if(GlobalFrequency < 0.05 && random.nextInt(2)!=0){
								for(int tries=0; tries < 10; tries++){
									int[] pt=new int[]{i0+(2*random.nextInt(2)-1)*(ContainerWidth + random.nextInt(ContainerWidth)),
												   	   0,
												       k0+(2*random.nextInt(2)-1)*(ContainerWidth + random.nextInt(ContainerWidth))};
									pt[1]=Building.findSurfaceJ(world,pt[0],pt[2],Building.WORLD_MAX_Y,true,3)+1;
									if(generate(pt[0], pt[1], pt[2])) 
										break;
								}
							}
							
							return true;
						}
						
						return false;
					}
				}

			);
		}
	}
	
	public final static String[][] DEFAULT_CA_RULES=new String[][]{
		//3-rule
		{"B3/S23",        "5", "Life - good for weird temples"},
		{"B36/S013468",   "3", "pillars and hands"},
		{"B367/S02347",   "2", "towers with interiors and chasms"},
		{"B34/S2356",     "3", "towers with hetrogenous shapes"},
		{"B368/S245",     "8", "Morley - good hanging bits"},
		{"B36/S125",      "4", "2x2 - pillar & arch temple/tower/statue"},
		{"B36/S23",       "4", "High Life - space invaders, hanging arms."},
		{"B3568/S148",    "4", "fuzzy stilts"},
		{"B3/S1245",      "8", "complex"},
		{"B3567/S13468",  "5", "fat fuzzy"},
		{"B356/S16",      "5", "fuzzy with spurs"},
		{"B3468/S123",    "3", "towers with arches"},
		{"B35678/S015678","2", "checkerboard"},
		{"B35678/S0156",  "15", "spermatazoa"},
		//2-rule
		{"B26/S12368",    "1", "mayan pyramid"},
		{"B248/S45",      "1", "gaudi pyramid"},
		{"B2457/S013458", "1", "complex interior pyramid"},
		//4-rule
		{"B45/S2345",     "6", "45-rule - square towers"},
	};
}
