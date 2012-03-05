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
 * TemplateWall reads in additional variables from a .tml file to define a wall template.
 * The class includes static functions used to load template folders and link together hierarchical templates.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Iterator;



public class TemplateWall extends TemplateTML{
	public final static String BUILDING_DIRECTORY_NAME="buildings";
	public final static String ALL_TEMPLATES="ALL", NO_TEMPLATES="NONE";
	private final static int DEFAULT_WATER_HEIGHT=666;
	public final static int[] ALL_BIOMES=null;
	
	public final static int NO_RULE=-1;
	public final static int MAX_STREET_DENSITY=20;

	public TemplateTML makeDefaultTower,makeCARuin;

	//USER MODIFIABLE PARAMETERS, values below are defaults
	public int[] Biomes=ALL_BIOMES;
	public boolean underground=false;
	public ArrayList<TemplateTML> buildings=null;
	public ArrayList<TemplateWall> streets=null;
	public int[][] buildingWeights;
	public int StreetDensity=6;
	public boolean LevelInterior=true;
	public int WHeight=7, WWidth=5, WalkHeight=0; //WalkHeight is the height in the template (embed will be subtracted)
	public int MinL=150, MaxL=1000;

	//default tower parameters
	public TemplateRule TowerRule=TemplateRule.RULE_NOT_PROVIDED, SpawnerRule=TemplateRule.RULE_NOT_PROVIDED,
						ChestRule=TemplateRule.RULE_NOT_PROVIDED, CARuinRule=TemplateRule.RULE_NOT_PROVIDED;
	public boolean MakeBuildings=true,MergeWalls=false, MakeEndTowers=true, MakeGatehouseTowers=true,
					MakeUndergroundEntranceways=true,PopulateFurniture=false, MakeDoors=false;
	public int LateralSmoothingScale=20, ConcaveUpSmoothingScale=20, ConcaveDownSmoothingScale=10;
	public int BuildingInterval=75;
	private int DefaultTowerWeight=1; 
	public int TowerXOffset=0;
	public float CircularProb=0.3F;
	private int SqrMinHeight=11, SqrMaxHeight=15, SqrMinWidth=7, SqrMaxWidth=7, CircMinHeight=11, CircMaxHeight=15, CircMinWidth=7, CircMaxWidth=7;
	private int[] SqrRoofStyles={4,1,1,1,1,0,0}, CircRoofStyles={3,0,0,0,1,1,0};
	private TemplateRule SqrRoofRule=null, CircRoofRule=null;
	
	//CARuin parameters
	public int CARuinWeight=0;
	public int CARuinContainerWidth=15;
	public int CARuinMinHeight=20;
	public int CARuinMaxHeight=35;
	ArrayList<byte[][]> CARuinAutomataRules=null;
	
	
	//****************************************  CONSTRUCTOR - WallStyle*************************************************************************************//
	public TemplateWall(File wallFile,HashMap<String,TemplateTML> buildingTemplateMap, BuildingExplorationHandler beh)  throws Exception{
		super(wallFile,beh);

		readTowerParameters();
		buildings=loadChildTemplates("building_templates",buildingTemplateMap);
		
		//build the weights index, first making dummy templates for default towers and CARuins
		makeCARuin=new TemplateTML(TemplateTML.CA_RUIN_CODE,CARuinWeight);
		buildings.add(makeCARuin);
		makeDefaultTower=new TemplateTML(TemplateTML.DEFAULT_TOWER_CODE,DefaultTowerWeight);
		buildings.add(makeDefaultTower);
		try{
			buildingWeights=TemplateTML.buildWeightsAndIndex(buildings);
		}catch(Exception e){
			if(e==ZERO_WEIGHT_EXCEPTION){ //all the templates had zero weight!
				//change default tower weight to 1 and rebuild index
				buildings.get(buildings.size()-1).weight=1;
				buildingWeights=TemplateTML.buildWeightsAndIndex(buildings);
			}else throw e;
		}
	}

	//****************************************  FUNCTION - loadTowerParameters *************************************************************************************//
	public void readTowerParameters() throws Exception{
		float mobProb=0.0F, pigZombieProb=0.0F, endermanProb=0.0F, caveSpiderProb=0.0F; //deprecated, for backawards compatability
		
		if(extraOptions.containsKey("biomes")) Biomes=BuildingExplorationHandler.readNamedCheckList(lw,Biomes,"=",(String)extraOptions.get("biomes"),Building.BIOME_NAMES,"ALL");
		if(extraOptions.containsKey("street_density")) StreetDensity=BuildingExplorationHandler.readIntParam(lw,StreetDensity,"=",(String)extraOptions.get("street_density"));
		if(extraOptions.containsKey("level_interior")) LevelInterior=BuildingExplorationHandler.readIntParam(lw,1,"=",(String)extraOptions.get("level_interior")) == 1;
		if(extraOptions.containsKey("walk_height")) WalkHeight=BuildingExplorationHandler.readIntParam(lw,WalkHeight,"=",(String)extraOptions.get("walk_height"));
		if(extraOptions.containsKey("min_length")) MinL=BuildingExplorationHandler.readIntParam(lw,MinL,"=",(String)extraOptions.get("min_length"));
		if(extraOptions.containsKey("max_length")) MaxL=BuildingExplorationHandler.readIntParam(lw,MaxL,"=",(String)extraOptions.get("max_length"));
		if(extraOptions.containsKey("tower_rule")) TowerRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("tower_rule"),rules);
		if(extraOptions.containsKey("building_interval")) BuildingInterval=BuildingExplorationHandler.readIntParam(lw,BuildingInterval,"=",(String)extraOptions.get("building_interval"));
		if(extraOptions.containsKey("make_buildings")) MakeBuildings=BuildingExplorationHandler.readIntParam(lw,1,"=",(String)extraOptions.get("make_buildings")) == 1;
		if(extraOptions.containsKey("make_gatehouse_towers")) MakeGatehouseTowers=BuildingExplorationHandler.readIntParam(lw,1,"=",(String)extraOptions.get("make_gatehouse_towers")) == 1;
		if(extraOptions.containsKey("make_end_towers")) MakeEndTowers=BuildingExplorationHandler.readIntParam(lw,1,"=",(String)extraOptions.get("make_end_towers")) == 1;
		if(extraOptions.containsKey("make_underground_entranceways")) MakeUndergroundEntranceways=BuildingExplorationHandler.readIntParam(lw,1,"=",(String)extraOptions.get("make_underground_entranceways")) == 1;
		if(extraOptions.containsKey("merge_walls")) MergeWalls=BuildingExplorationHandler.readIntParam(lw,0,"=",(String)extraOptions.get("merge_walls")) == 1;
		if(extraOptions.containsKey("lateral_smoothing_scale")) LateralSmoothingScale=BuildingExplorationHandler.readIntParam(lw,LateralSmoothingScale,"=",(String)extraOptions.get("lateral_smoothing_scale"));
		if(extraOptions.containsKey("concave_up_smoothing_scale")) ConcaveUpSmoothingScale=BuildingExplorationHandler.readIntParam(lw,ConcaveUpSmoothingScale,"=",(String)extraOptions.get("concave_up_smoothing_scale"));
		if(extraOptions.containsKey("concave_down_smoothing_scale")) ConcaveDownSmoothingScale=BuildingExplorationHandler.readIntParam(lw,ConcaveDownSmoothingScale,"=",(String)extraOptions.get("concave_down_smoothing_scale"));
		
		//default tower variables
		if(extraOptions.containsKey("default_tower_weight")) DefaultTowerWeight=BuildingExplorationHandler.readIntParam(lw,DefaultTowerWeight,"=",(String)extraOptions.get("default_tower_weight"));
		if(extraOptions.containsKey("tower_offset")) TowerXOffset=BuildingExplorationHandler.readIntParam(lw,TowerXOffset,"=",(String)extraOptions.get("tower_offset"));
		if(extraOptions.containsKey("spawner_rule")) SpawnerRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("spawner_rule"),rules);
		if(extraOptions.containsKey("populate_furniture")) PopulateFurniture=BuildingExplorationHandler.readFloatParam(lw,0,"=",(String)extraOptions.get("populate_furniture")) == 1;
		if(extraOptions.containsKey("make_doors")) MakeDoors=BuildingExplorationHandler.readFloatParam(lw,0,"=",(String)extraOptions.get("make_doors")) == 1;
		if(extraOptions.containsKey("circular_probability")) CircularProb=BuildingExplorationHandler.readFloatParam(lw,CircularProb,"=",(String)extraOptions.get("circular_probability"));
		if(extraOptions.containsKey("chest_rule")) ChestRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("chest_rule"),rules);
		if(extraOptions.containsKey("square_min_height")) SqrMinHeight=BuildingExplorationHandler.readIntParam(lw,SqrMinHeight,"=",(String)extraOptions.get("square_min_height"));
		if(extraOptions.containsKey("square_max_height")) SqrMaxHeight=BuildingExplorationHandler.readIntParam(lw,SqrMaxHeight,"=",(String)extraOptions.get("square_max_height"));
		if(extraOptions.containsKey("square_min_width")) SqrMinWidth=BuildingExplorationHandler.readIntParam(lw,SqrMinWidth,"=",(String)extraOptions.get("square_min_width"));
		if(extraOptions.containsKey("square_max_width")) SqrMaxWidth=BuildingExplorationHandler.readIntParam(lw,SqrMaxWidth,"=",(String)extraOptions.get("square_max_width"));
		if(extraOptions.containsKey("square_roof_styles")) SqrRoofStyles=BuildingExplorationHandler.readNamedCheckList(lw,SqrRoofStyles,"=",(String)extraOptions.get("square_roof_styles"),BuildingTower.ROOFSTYLE_NAMES,"");
		if(extraOptions.containsKey("square_roof_rule")) SqrRoofRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("square_roof_rule"),rules);
		if(extraOptions.containsKey("circular_tower_min_height")) CircMinHeight=BuildingExplorationHandler.readIntParam(lw,CircMinHeight,"=",(String)extraOptions.get("circular_tower_min_height"));
		if(extraOptions.containsKey("circular_tower_max_height")) CircMaxHeight=BuildingExplorationHandler.readIntParam(lw,CircMaxHeight,"=",(String)extraOptions.get("circular_tower_max_height"));
		if(extraOptions.containsKey("circular_tower_min_width")) CircMinWidth=BuildingExplorationHandler.readIntParam(lw,CircMinWidth,"=",(String)extraOptions.get("circular_tower_min_width"));
		if(extraOptions.containsKey("circular_tower_max_width")) CircMaxWidth=BuildingExplorationHandler.readIntParam(lw,CircMaxWidth,"=",(String)extraOptions.get("circular_tower_max_width"));
		if(extraOptions.containsKey("circular_tower_roof_styles")) CircRoofStyles=BuildingExplorationHandler.readNamedCheckList(lw,CircRoofStyles,"=",(String)extraOptions.get("circular_tower_roof_styles"),BuildingTower.ROOFSTYLE_NAMES,"");
		if(extraOptions.containsKey("circular_tower_roof_rule")) CircRoofRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("circular_tower_roof_rule"),rules);

		//default tower variables (deprecated)
		if(extraOptions.containsKey("mob_probability")) mobProb=BuildingExplorationHandler.readFloatParam(lw,mobProb,"=",(String)extraOptions.get("mob_probability"));
		if(extraOptions.containsKey("pig_zombie_probability")) pigZombieProb=BuildingExplorationHandler.readFloatParam(lw,pigZombieProb,"=",(String)extraOptions.get("pig_zombie_probability"));
		if(extraOptions.containsKey("enderman_probability")) endermanProb=BuildingExplorationHandler.readFloatParam(lw,endermanProb,"=",(String)extraOptions.get("enderman_probability"));
		if(extraOptions.containsKey("cave_spider_probability")) caveSpiderProb=BuildingExplorationHandler.readFloatParam(lw,caveSpiderProb,"=",(String)extraOptions.get("cave_spider_probability"));
		
		//caruin variables
		if(extraOptions.containsKey("ca_ruin_rule")) CARuinRule=explorationHandler.readRuleIdOrRule("=",(String)extraOptions.get("ca_ruin_rule"),rules);
		if(extraOptions.containsKey("ca_ruin_weight")) CARuinWeight=BuildingExplorationHandler.readIntParam(lw,CARuinWeight,"=",(String)extraOptions.get("ca_ruin_weight"));
		if(extraOptions.containsKey("ca_ruin_min_height")) CARuinMinHeight=BuildingExplorationHandler.readIntParam(lw,CARuinMinHeight,"=",(String)extraOptions.get("ca_ruin_min_height"));
		if(extraOptions.containsKey("ca_ruin_max_height")) CARuinMaxHeight=BuildingExplorationHandler.readIntParam(lw,CARuinMaxHeight,"=",(String)extraOptions.get("ca_ruin_max_height"));
		if(extraOptions.containsKey("ca_ruin_max_width")) CARuinContainerWidth=BuildingExplorationHandler.readIntParam(lw,CARuinContainerWidth,"=",(String)extraOptions.get("ca_ruin_max_width"));
		if(extraOptions.containsKey("ca_ruin_automata_rules")) CARuinAutomataRules=BuildingExplorationHandler.readAutomataList(lw,"=",(String)extraOptions.get("ca_ruin_automata_rules"));
		
		//&&&&&&&&&&&&&&&&&&&&&&  post-processing  &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		if(MaxL <= MinL) MaxL=MinL+1;
		if(StreetDensity<0) StreetDensity=0;
		if(StreetDensity>MAX_STREET_DENSITY) StreetDensity=MAX_STREET_DENSITY;

		WWidth = width;
		WHeight = length - embed;
		WalkHeight-=embed;
		if(!readInWaterHeight && waterHeight>=WalkHeight) waterHeight=WalkHeight-1;
		if(DefaultTowerWeight<0) DefaultTowerWeight=0;

		if(SqrMinWidth < BuildingTower.TOWER_UNIV_MIN_WIDTH) SqrMinWidth=BuildingTower.TOWER_UNIV_MIN_WIDTH;
		if(SqrMaxWidth < SqrMinWidth) SqrMaxWidth=SqrMinWidth;
		if(SqrMinHeight < WalkHeight +4) SqrMinHeight=WalkHeight +4;
		if(SqrMaxHeight < SqrMinHeight) SqrMaxHeight=SqrMinHeight;

		if(BuildingInterval < SqrMinWidth ) BuildingInterval = SqrMinWidth + 1;
		
		if(CircMinWidth < BuildingTower.TOWER_UNIV_MIN_WIDTH) CircMinWidth=BuildingTower.TOWER_UNIV_MIN_WIDTH;
		if(CircMaxWidth < CircMinWidth ) CircMaxWidth=CircMinWidth;
		if(CircMaxWidth >= Building.CIRCLE_CRENEL.length) CircMaxWidth=Building.CIRCLE_CRENEL.length - 1;
		if(CircMinWidth >= Building.CIRCLE_CRENEL.length) CircMinWidth=Building.CIRCLE_CRENEL.length - 1;
		if(CircMinHeight < WalkHeight +4) CircMinHeight=WalkHeight +4;
		if(CircMaxHeight < CircMinHeight ) CircMaxHeight=CircMinHeight;
		
		if(CARuinAutomataRules==null || CARuinAutomataRules.size()==0) CARuinWeight=0;
		if(CARuinWeight > 0 && CARuinRule==TemplateRule.RULE_NOT_PROVIDED) CARuinRule=TowerRule;
		if(CARuinMaxHeight < CARuinMinHeight) CARuinMaxHeight=CARuinMinHeight;
		
		if(TowerRule==null) throw new Exception("No valid rule provided for tower block!");
		
		//spawner rule logic
		if(SpawnerRule==TemplateRule.RULE_NOT_PROVIDED){
			//try the deprecated mob probabilities
			if(mobProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.UPRIGHT_SPAWNER_ID,0}, (int)(mobProb*100));
			else if(pigZombieProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.PIG_ZOMBIE_SPAWNER_ID,0}, (int)(pigZombieProb*100));
			else if(endermanProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.ENDERMAN_SPAWNER_ID,0}, (int)(endermanProb*100));
			else if(caveSpiderProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.CAVE_SPIDER_SPAWNER_ID,0}, (int)(caveSpiderProb*100));
		}
		
		if(Biomes!=ALL_BIOMES && Biomes[Building.BIOME_UNDERGROUND]>0){
			underground=true;
			Biomes=ALL_BIOMES;
		}

	}

	//****************************************  FUNCTION - loadChildTemplates *************************************************************************************//
	public ArrayList<TemplateTML> loadChildTemplates(String listVarString,HashMap<String,TemplateTML> childTemplateMap){
		ArrayList<TemplateTML> childTemplates=new ArrayList<TemplateTML>();
		if(!extraOptions.containsKey(listVarString)) return childTemplates;
		String[] names = (((String)extraOptions.get(listVarString)).split("="))[1].split(",");
		
		String templateListStr=(String)extraOptions.get(listVarString);
		if(templateListStr==null) return childTemplates;
		

		for(String name : names){
			name=name.trim();
			if(name.toUpperCase().equals(NO_TEMPLATES)) return new ArrayList<TemplateTML>();
			if(name.toUpperCase().equals(ALL_TEMPLATES)){
				childTemplates.addAll(childTemplateMap.values());
				break;
			}
			if(childTemplateMap.containsKey(name.trim()))
				childTemplates.add((TemplateTML) childTemplateMap.get(name.trim()) );
		}
		return childTemplates;
	}

	public ArrayList<TemplateWall> loadChildStyles(String listVarString,HashMap<String,TemplateWall> childTemplateMap){
		ArrayList<TemplateWall> childTemplates=new ArrayList<TemplateWall>();
		if(!extraOptions.containsKey(listVarString)) return childTemplates;
		String[] names = (((String)extraOptions.get(listVarString)).split("="))[1].split(",");
		
		for(String name : names){
			name=name.trim();
			if(name.toUpperCase().equals(NO_TEMPLATES)) return new ArrayList<TemplateWall>();
			if(name.toUpperCase().equals(ALL_TEMPLATES)){
				childTemplates.addAll(childTemplateMap.values());
				break;
			}
			if(childTemplateMap.containsKey(name.trim())){
				TemplateWall ws=childTemplateMap.get(name.trim());
				ws.Biomes=ALL_BIOMES;
				childTemplates.add(ws );
			}
		}
		return childTemplates;
	}
	

	//****************************************  FUNCTIONS - tower accessors *************************************************************************************//
	
	public int pickRoofStyle(boolean circular, Random random){ 
		return circular ? Building.pickWeightedOption(random,CircRoofStyles,BuildingTower.ROOF_STYLE_IDS) : 
			Building.pickWeightedOption(random,SqrRoofStyles,BuildingTower.ROOF_STYLE_IDS);
	}
	
	public int getTMinWidth(boolean circular){ return circular ? CircMinWidth : SqrMinWidth; }

	public int getTMaxWidth(boolean circular){ return circular ? CircMaxWidth: SqrMaxWidth; }

	public int getTMinHeight(boolean circular){ return circular ? CircMinHeight : SqrMinHeight; }

	public int getTMaxHeight(boolean circular){ return circular ? CircMaxHeight: SqrMaxHeight; }
	
	public TemplateRule getRoofRule(boolean circular) { return circular ? CircRoofRule: SqrRoofRule; }

	public int pickTWidth(boolean circular,Random random){ 
		return circular ? CircMinWidth+ random.nextInt(CircMaxWidth - CircMinWidth + 1) : 
			                    SqrMinWidth + random.nextInt(SqrMaxWidth - SqrMinWidth + 1);
	}

	public int pickTHeight(boolean circular,Random random){ 
		return circular ? CircMinHeight + random.nextInt(CircMaxHeight - CircMinHeight + 1) : 
			                     SqrMinHeight + random.nextInt(SqrMaxHeight - SqrMinHeight + 1);
	}

	//****************************************  FUNCTION - loadTemplatesFromDir *************************************************************************************//
	public static ArrayList<TemplateTML> loadTemplatesFromDir(File tmlDirectory, BuildingExplorationHandler explorationHandler){
		ArrayList<TemplateTML> templates= new ArrayList<TemplateTML>();
		for( File f : tmlDirectory.listFiles() ) {
			if(getFileType(f.getName()).equals("tml")){
				try{
					TemplateTML t = new TemplateTML(f,explorationHandler).buildLayout();
					templates.add(t);	
				}catch(Exception e){
					if(e==TemplateTML.ZERO_WEIGHT_EXCEPTION){
						explorationHandler.lw.println("Did not load "+f.getName()+", weight was zero.");
					}else{
						explorationHandler.lw.println("\nThere was a problem loading the .tml file " + f.getName());
						if(!e.getMessage().startsWith(TemplateRule.BLOCK_NOT_REGISTERED_ERROR_PREFIX)){
							e.printStackTrace(explorationHandler.lw);
							explorationHandler.lw.println();
						}else explorationHandler.lw.println(e.getMessage());
					}
				}
		}}
		return templates;
	}

	//****************************************  FUNCTION - loadWallStylesFromDir *************************************************************************************//
	public static ArrayList<TemplateWall> loadWallStylesFromDir(File stylesDirectory, BuildingExplorationHandler explorationHandler) throws Exception{
		if(!stylesDirectory.exists()) 
			throw new Exception("Could not find directory /"+stylesDirectory.getName()+" in the resource folder "+stylesDirectory.getParent()+"!");
		
		//load buildings
		explorationHandler.lw.println("\nLoading building subfolder in "+stylesDirectory+"\\"+BUILDING_DIRECTORY_NAME+"...");
		HashMap<String,TemplateTML> buildingTemplates=new HashMap<String,TemplateTML>();
		try{
			Iterator<TemplateTML> itr=loadTemplatesFromDir(new File(stylesDirectory,BUILDING_DIRECTORY_NAME),explorationHandler).iterator();
			while(itr.hasNext()){
				TemplateTML t=itr.next();
				buildingTemplates.put(t.name,t);
			}
		} catch(Exception e){
			explorationHandler.lw.println("No buildings folder for "+stylesDirectory.getName()+e.toString());
		}

		for(int i=0;i<10;i++) {}
		
		//load walls
		explorationHandler.lw.println("\nLoading wall styles from directory "+stylesDirectory+"...");
			
		ArrayList<TemplateWall> styles = new ArrayList<TemplateWall>();
		for( File f : stylesDirectory.listFiles() ) {
			if(getFileType(f.getName()).equals("tml")){
				try{
					TemplateWall ws=new TemplateWall(f,buildingTemplates,explorationHandler);
					styles.add(ws);
				}catch(Exception e){
					if(e==TemplateTML.ZERO_WEIGHT_EXCEPTION){
						explorationHandler.lw.println("Did not load "+f.getName()+", weight was zero.");
					}else{
						explorationHandler.lw.println("\nError loading wall style " + f.getName());
						if(!e.getMessage().startsWith(TemplateRule.BLOCK_NOT_REGISTERED_ERROR_PREFIX)){
							e.printStackTrace(explorationHandler.lw);
							explorationHandler.lw.println();
						}else explorationHandler.lw.println(e.getMessage());
					}
				}
			}
		}
		explorationHandler.lw.flush();
		if(styles.size()==0) throw new Exception("Did not find any valid wall styles!");
		return styles;
	}
	
	
	public static TemplateWall pickBiomeWeightedWallStyle(ArrayList<TemplateWall> styles,World world, int i, int k, Random random, boolean ignoreBiomes){
		//BUKKIT PORT
		//int biome=getBiomeNum(world.getBiome(i,k));
		int biome=Building.getBiomeNum(world.getWorldChunkManager().getBiomeGenAt(i,k));
		if((biome < 0 || biome >= Building.BIOME_NAMES.length) && !ignoreBiomes) return null;
	  	int sum=0;
	  	for(TemplateWall ws : styles){
	  		if(ignoreBiomes || ws.Biomes == ALL_BIOMES || ws.Biomes[biome]>0) sum+=ws.weight;
	  	}
	  	if(sum<=0) return null;
	  

	  	int s=random.nextInt(sum);
	  	sum=0;
	  	for(TemplateWall ws : styles){
	  		if(ignoreBiomes || ws.Biomes == ALL_BIOMES || ws.Biomes[biome]>0) sum+=ws.weight;
	  		if(sum>s) return ws;
	  	}
	  	return null;
	}
	
	//BUKKIT PORT
	/*
	public static int getBiomeNum( Biome biome ) {
        if( biome == Biome.RAINFOREST ) 			return BIOME_RAINFOREST;
        else if( biome == Biome.SWAMPLAND )	 		return BIOME_SWAMPLAND;
        else if( biome == Biome.SEASONAL_FOREST ) 	return BIOME_SEASONALFOREST;
        else if( biome == Biome.FOREST ) 			return BIOME_FOREST;
        else if( biome == Biome.SAVANNA ) 			return BIOME_SAVANNA;
        else if( biome == Biome.SHRUBLAND ) 		return BIOME_SHRUBLAND;
        else if( biome == Biome.TAIGA ) 			return BIOME_TAIGA;
		else if( biome == Biome.DESERT ) 			return BIOME_DESERT;
        else if( biome == Biome.PLAINS ) 			return BIOME_PLAINS;
        else if( biome == Biome.ICE_DESERT ) 		return BIOME_ICEDESERT;
        else if( biome == Biome.TUNDRA ) 			return BIOME_TUNDRA;
        else if( biome == Biome.HELL ) 				return BIOME_HELL;
        else if( biome == Biome.SKY ) 				return BIOME_SKY;
		
		return BIOME_FOREST;
	}
	*/
	
	
	

	//****************************************  FUNCTION - loadStreets *************************************************************************************//
	public static void loadStreets(ArrayList<TemplateWall> cityStyles,File streetsDirectory, BuildingExplorationHandler explorationHandler) throws Exception{
		//streets, don't print error if directory DNE
		HashMap<String,TemplateWall> streetTemplateMap=new HashMap<String,TemplateWall>();
		Iterator<TemplateWall> itr;
		try{
			explorationHandler.lw.println("\nLoading streets subfolder in "+streetsDirectory+"...");
			itr=loadWallStylesFromDir(streetsDirectory,explorationHandler).iterator();
			while(itr.hasNext()){
				TemplateWall cs=itr.next();
				streetTemplateMap.put(cs.name,cs);
			}
		} catch(Exception e){
			explorationHandler.lw.println("No street folder for "+streetsDirectory.getName()+e.toString());
		}

		explorationHandler.lw.println();
		itr=cityStyles.iterator();
		while(itr.hasNext()){
			TemplateWall cs=itr.next();
			cs.streets=cs.loadChildStyles("street_templates",streetTemplateMap);
			if(cs.streets.size()==0 && !cs.underground){
				itr.remove();
				explorationHandler.lw.println("No valid street styles for "+cs.name+". Disabling this city style.");
			}
			//else cs.streetWeights=buildWeightsAndIndex(cs.streets);
		}
		if(cityStyles.size()==0) throw new Exception("Did not find any valid city styles that had street styles!");
	}

	//****************************************  FUNCTION - getFileType *************************************************************************************//
	private static String getFileType( String s ) {
		int mid = s.lastIndexOf( "." );
		return s.substring( mid + 1, s.length() );
	}

}



