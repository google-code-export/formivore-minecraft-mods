package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Modloader handle for Great Wall Mod, reads in from SETTINGS_FILE.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Iterator;



public class WallStyle extends TemplateTML{
	public final static String BUILDING_DIRECTORY_NAME="buildings";
	public final static String ALL_TEMPLATES="ALL", NO_TEMPLATES="NONE";
	public final static String[] BIOME_NAMES={"Rainforest","Swampland","Seasonal Forest","Forest","Savanna","Shrubland","Taiga","Desert","Plains","Ice Desert","Tundra","Hell","Sky","Hills","Ocean","Underground"};
	public final static int[] ALL_BIOMES=null;
	public final static int BIOME_RAINFOREST = 0, BIOME_SWAMPLAND = 1, BIOME_SEASONALFOREST = 2, BIOME_FOREST = 3,BIOME_SAVANNA = 4,
							BIOME_SHRUBLAND = 5, BIOME_TAIGA = 6, BIOME_DESERT = 7, BIOME_PLAINS = 8, BIOME_ICEDESERT = 9,
							BIOME_TUNDRA = 10, BIOME_HELL = 11,BIOME_SKY=12, BIOME_HILLS=13,BIOME_OCEAN=14,BIOME_UNDERGROUND=15;
	
	public final static TemplateTML DEFAULT_TOWER=null;
	public final static int NO_RULE=-1;
	public final static int MAX_STREET_DENSITY=20;


	//USER MODIFIABLE PARAMETERS, values below are defaults
	public int[] Biomes=ALL_BIOMES;
	public boolean underground=false;
	public ArrayList<TemplateTML> buildings=null;
	public ArrayList<WallStyle> streets=null;
	public int[][] buildingWeights;
	public int StreetDensity=6;
	public boolean LevelInterior=true;
	public int WHeight=7, WWidth=5, WalkHeight=0; //walk height is the height in the template (embed will be subtracted)
	public int MinL=150, MaxL=1000;

	//default tower parameters
	public TemplateRule TowerRule=null, SpawnerRule=null, ChestRule=null;
	//public boolean RandomizeBuildingIntervals=false;
	public boolean MakeBuildings=true,MergeWalls=false, EndTowers=true, GatehouseTowers=true,PopulateFurniture=false, MakeDoors=false;
	public int BuildingInterval=75;
	public int DefaultTowerWeight=1;
	public int TowerXOffset=0;
	public int SpawnerCount=1;
	public float CircularProb=0.3F;
	private int SqrMinHeight=11, SqrMaxHeight=15, SqrMinWidth=7, SqrMaxWidth=7, CircMinHeight=11, CircMaxHeight=15, CircMinWidth=7, CircMaxWidth=7;
	private int[] SqrRoofStyles={4,1,1,1,1,0,0}, CircRoofStyles={3,0,0,0,1,1,0};
	private TemplateRule SqrRoofRule=null, CircRoofRule=null;
	
	//instance fixed variables, set these with pickFixedRulesAndStyles
	//public int pickedRoofStyle=0;
	//public boolean pickedCircular=false;
	


	//****************************************  CONSTRUCTOR - WallStyle*************************************************************************************//
	public WallStyle(File wallFile,HashMap<String,TemplateTML> buildingTemplateMap, PrintWriter lw_)  throws Exception{
		super(wallFile,lw_);

		readTowerParameters();
		buildings=loadChildTemplates("building_templates",buildingTemplateMap);
		buildings.add(DEFAULT_TOWER);
		buildingWeights=TemplateTML.buildWeightsAndIndex(buildings,DefaultTowerWeight);
	}

	//****************************************  FUNCTION - loadTowerParameters *************************************************************************************//
	public void readTowerParameters() throws Exception{
		float mobProb=0.0F, pigZombieProb=0.0F, endermanProb=0.0F, caveSpiderProb=0.0F; //deprecated, for backawards compatability
		
		if(extraOptions.containsKey("biomes")) Biomes=readNamedCheckList(lw,Biomes,"=",(String)extraOptions.get("biomes"),BIOME_NAMES,"ALL");
		if(extraOptions.containsKey("street_density")) StreetDensity=readIntParam(lw,StreetDensity,"=",(String)extraOptions.get("street_density"));
		if(extraOptions.containsKey("level_interior")) LevelInterior=readIntParam(lw,1,"=",(String)extraOptions.get("level_interior")) == 1;
		if(extraOptions.containsKey("walk_height")) WalkHeight=readIntParam(lw,WalkHeight,"=",(String)extraOptions.get("walk_height"));
		if(extraOptions.containsKey("min_length")) MinL=readIntParam(lw,MinL,"=",(String)extraOptions.get("min_length"));
		if(extraOptions.containsKey("max_length")) MaxL=readIntParam(lw,MaxL,"=",(String)extraOptions.get("max_length"));
		if(extraOptions.containsKey("tower_rule")) TowerRule=readRuleIdOrRule(null,"=",(String)extraOptions.get("tower_rule"));
		if(extraOptions.containsKey("building_interval")) BuildingInterval=readIntParam(lw,BuildingInterval,"=",(String)extraOptions.get("building_interval"));
		if(extraOptions.containsKey("make_buildings")) MakeBuildings=readIntParam(lw,1,"=",(String)extraOptions.get("make_buildings")) == 1;
		if(extraOptions.containsKey("make_gatehouse_towers")) GatehouseTowers=readIntParam(lw,1,"=",(String)extraOptions.get("make_gatehouse_towers")) == 1;
		if(extraOptions.containsKey("make_end_towers")) EndTowers=readIntParam(lw,1,"=",(String)extraOptions.get("make_end_towers")) == 1;
		if(extraOptions.containsKey("merge_walls")) MergeWalls=readIntParam(lw,0,"=",(String)extraOptions.get("merge_walls")) == 1;
		if(extraOptions.containsKey("default_tower_weight")) DefaultTowerWeight=readIntParam(lw,DefaultTowerWeight,"=",(String)extraOptions.get("default_tower_weight"));
		if(extraOptions.containsKey("tower_offset")) TowerXOffset=readIntParam(lw,TowerXOffset,"=",(String)extraOptions.get("tower_offset"));
		if(extraOptions.containsKey("spawner_rule")) SpawnerRule=readRuleIdOrRule(BuildingTower.NO_SPAWNER_RULE ,"=",(String)extraOptions.get("spawner_rule"));
		if(extraOptions.containsKey("spawner_count")) SpawnerCount=readIntParam(lw,SpawnerCount,"=",(String)extraOptions.get("spawner_count"));
		if(extraOptions.containsKey("mob_probability")) mobProb=readFloatParam(lw,mobProb,"=",(String)extraOptions.get("mob_probability"));
		if(extraOptions.containsKey("pig_zombie_probability")) pigZombieProb=readFloatParam(lw,pigZombieProb,"=",(String)extraOptions.get("pig_zombie_probability"));
		if(extraOptions.containsKey("enderman_probability")) endermanProb=readFloatParam(lw,endermanProb,"=",(String)extraOptions.get("enderman_probability"));
		if(extraOptions.containsKey("cave_spider_probability")) caveSpiderProb=readFloatParam(lw,caveSpiderProb,"=",(String)extraOptions.get("cave_spider_probability"));
		if(extraOptions.containsKey("populate_furniture")) PopulateFurniture=readFloatParam(lw,0,"=",(String)extraOptions.get("populate_furniture")) == 1;
		if(extraOptions.containsKey("make_doors")) MakeDoors=readFloatParam(lw,0,"=",(String)extraOptions.get("make_doors")) == 1;
		if(extraOptions.containsKey("circular_probability")) CircularProb=readFloatParam(lw,CircularProb,"=",(String)extraOptions.get("circular_probability"));
		if(extraOptions.containsKey("chest_rule")) ChestRule=readRuleIdOrRule(BuildingTower.NO_CHEST_RULE ,"=",(String)extraOptions.get("chest_rule"));
		if(extraOptions.containsKey("square_min_height")) SqrMinHeight=readIntParam(lw,SqrMinHeight,"=",(String)extraOptions.get("square_min_height"));
		if(extraOptions.containsKey("square_max_height")) SqrMaxHeight=readIntParam(lw,SqrMaxHeight,"=",(String)extraOptions.get("square_max_height"));
		if(extraOptions.containsKey("square_min_width")) SqrMinWidth=readIntParam(lw,SqrMinWidth,"=",(String)extraOptions.get("square_min_width"));
		if(extraOptions.containsKey("square_max_width")) SqrMaxWidth=readIntParam(lw,SqrMaxWidth,"=",(String)extraOptions.get("square_max_width"));
		if(extraOptions.containsKey("square_roof_styles")) SqrRoofStyles=readNamedCheckList(lw,SqrRoofStyles,"=",(String)extraOptions.get("square_roof_styles"),BuildingTower.ROOFSTYLE_NAMES,"");
		if(extraOptions.containsKey("square_roof_rule")) SqrRoofRule=readRuleIdOrRule(BuildingTower.DEFAULT_ROOF_RULE ,"=",(String)extraOptions.get("square_roof_rule"));
		if(extraOptions.containsKey("circular_tower_min_height")) CircMinHeight=readIntParam(lw,CircMinHeight,"=",(String)extraOptions.get("circular_tower_min_height"));
		if(extraOptions.containsKey("circular_tower_max_height")) CircMaxHeight=readIntParam(lw,CircMaxHeight,"=",(String)extraOptions.get("circular_tower_max_height"));
		if(extraOptions.containsKey("circular_tower_min_width")) CircMinWidth=readIntParam(lw,CircMinWidth,"=",(String)extraOptions.get("circular_tower_min_width"));
		if(extraOptions.containsKey("circular_tower_max_width")) CircMaxWidth=readIntParam(lw,CircMaxWidth,"=",(String)extraOptions.get("circular_tower_max_width"));
		if(extraOptions.containsKey("circular_tower_roof_styles")) CircRoofStyles=readNamedCheckList(lw,CircRoofStyles,"=",(String)extraOptions.get("circular_tower_roof_styles"),BuildingTower.ROOFSTYLE_NAMES,"");
		if(extraOptions.containsKey("circular_tower_roof_rule")) CircRoofRule=readRuleIdOrRule(BuildingTower.DEFAULT_ROOF_RULE ,"=",(String)extraOptions.get("circular_tower_roof_rule"));

		if(MaxL <= MinL) MaxL=MinL+1;
		if(StreetDensity<0) StreetDensity=0;
		if(StreetDensity>MAX_STREET_DENSITY) StreetDensity=MAX_STREET_DENSITY;

		WWidth = width;
		WHeight = length - embed;
		WalkHeight-=embed;
		if(DefaultTowerWeight<0) DefaultTowerWeight=0;

		if(SqrMinWidth < BuildingTower.TOWER_UNIV_MIN_WIDTH) SqrMinWidth=BuildingTower.TOWER_UNIV_MIN_WIDTH;
		if(SqrMaxWidth < SqrMinWidth) SqrMaxWidth=SqrMinWidth;
		if(SqrMinHeight < WalkHeight +4) SqrMinHeight=WalkHeight +4;
		if(SqrMaxHeight < SqrMinHeight) SqrMaxHeight=SqrMinHeight;


		if(CircMinWidth < BuildingTower.TOWER_UNIV_MIN_WIDTH) CircMinWidth=BuildingTower.TOWER_UNIV_MIN_WIDTH;
		if(CircMaxWidth < CircMinWidth ) CircMaxWidth=CircMinWidth;
		if(CircMaxWidth >= Building.CIRCLE_CRENEL.length) CircMaxWidth=Building.CIRCLE_CRENEL.length - 1;
		if(CircMinWidth >= Building.CIRCLE_CRENEL.length) CircMinWidth=Building.CIRCLE_CRENEL.length - 1;
		if(CircMinHeight < WalkHeight +4) CircMinHeight=WalkHeight +4;
		if(CircMaxHeight < CircMinHeight ) CircMaxHeight=CircMinHeight;

		if(BuildingInterval < SqrMinWidth ) BuildingInterval = SqrMinWidth + 1;
		
		if(TowerRule==null) throw new Exception("No valid rule provided for tower block!");
		
		//spawner rule logic
		if(SpawnerRule==BuildingTower.NO_SPAWNER_RULE){
			//try the deprecated mob probabilities
			if(mobProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.UPRIGHT_SPAWNER_ID,0}, (int)(mobProb*100));
			else if(pigZombieProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.PIG_ZOMBIE_SPAWNER_ID,0}, (int)(pigZombieProb*100));
			else if(endermanProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.ENDERMAN_SPAWNER_ID,0}, (int)(endermanProb*100));
			else if(caveSpiderProb>0.0F) SpawnerRule=new TemplateRule(new int[]{Building.CAVE_SPIDER_SPAWNER_ID,0}, (int)(caveSpiderProb*100));
		}else{
			for(int blockID : SpawnerRule.getBlockIDs()){
				if(!Building.IS_SPAWNER_BLOCK[blockID]){
					SpawnerRule= new TemplateRule(new int[]{Building.UPRIGHT_SPAWNER_ID,0}, SpawnerRule.chance);
					break;
				}
			}
		}
		
		if(Biomes!=ALL_BIOMES && Biomes[BIOME_UNDERGROUND]>0){
			underground=true;
			Biomes=ALL_BIOMES;
		}

	}

	//****************************************  FUNCTION - loadChildTemplates *************************************************************************************//
	public ArrayList<TemplateTML> loadChildTemplates(String listVarString,HashMap<String,TemplateTML> childTemplateMap){
		ArrayList<TemplateTML> childTemplates=new ArrayList<TemplateTML>();
		String templateListStr=(String)extraOptions.get(listVarString);

		if(templateListStr==null || templateListStr!=null && templateListStr.equals(listVarString+"="+NO_TEMPLATES)) return childTemplates;
		if(templateListStr.equals(listVarString+"="+ALL_TEMPLATES)){
			childTemplates.addAll(childTemplateMap.values());
		}
		else{
			String[] names = (templateListStr.split("="))[1].split(",");
			for(String name : names)
				if(childTemplateMap.containsKey(name.trim()))
					childTemplates.add((TemplateTML) childTemplateMap.get(name.trim()) );
		}
		return childTemplates;
	}

	//because Java generics just have to suck so hard
	public ArrayList<WallStyle> loadChildStyles(String listVarString,HashMap<String,WallStyle> childTemplateMap){
		ArrayList<WallStyle> childTemplates=new ArrayList<WallStyle>();
		String templateListStr=(String)extraOptions.get(listVarString);

		if(templateListStr==null || templateListStr!=null && templateListStr.equals(listVarString+"="+NO_TEMPLATES)) return childTemplates;
		if(templateListStr.equals(listVarString+"="+ALL_TEMPLATES) ){
			childTemplates.addAll(childTemplateMap.values());
		}
		else{
			String[] names = templateListStr.split("=")[1].split(",");
			for(String name : names){
				if(childTemplateMap.containsKey(name.trim())){
					WallStyle ws=childTemplateMap.get(name.trim());
					ws.Biomes=ALL_BIOMES;
					childTemplates.add(ws );
				}
			}
		}
		return childTemplates;
	}
	

	//****************************************  FUNCTIONS - tower accessors *************************************************************************************//
	
	public int pickRoofStyle(boolean circular, Random random){ 
		return circular ? Building.selectWeightedOption(random,CircRoofStyles,BuildingTower.ROOF_STYLE_IDS) : 
			Building.selectWeightedOption(random,SqrRoofStyles,BuildingTower.ROOF_STYLE_IDS);
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
	
	public static int[] readNamedCheckList(PrintWriter lw,int[] defaultVals,String splitString, String read, String[] names, String allStr){
		if(defaultVals==null || names.length!=defaultVals.length) defaultVals=new int[names.length];
		try{
			int[] newVals=new int[names.length];
			for(int i=0;i<newVals.length;i++) newVals[i]=0;
			if((read.split(splitString)[1]).trim().equals(allStr)){
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
	
	//if an integer ruleId: try reading from rules and return.
	//If a rule: parse the rule, add it to rules, and return.
	public TemplateRule readRuleIdOrRule(TemplateRule defaultRule, String splitString, String read){
		String postSplit=read.split(splitString)[1].trim();
		try{
			int ruleId=Integer.parseInt(postSplit);
			return rules[ruleId];
		} catch(NumberFormatException e) { 
			try{
				TemplateRule r=new TemplateRule(postSplit);
				return r;
				//TemplateRule[] newRules= new TemplateRule[rules.length+1];
				//for(int m=0; m<rules.length; m++) newRules[m]=rules[m];
				//newRules[rules.length]=r;
				//rules=newRules;
			}catch(Exception re){
				lw.println("Error parsing rule: "+re.toString()+". Line:"+read);
			}
		}catch(Exception e) { 
			lw.println("Error reading block rule for variable: "+e.toString());
			lw.println("Line:"+read); 
		}
		return defaultRule;
	}

	//****************************************  FUNCTION - loadTemplatesFromDir *************************************************************************************//
	public static ArrayList<TemplateTML> loadTemplatesFromDir(File tmlDirectory, PrintWriter lw){
		ArrayList<TemplateTML> templates= new ArrayList<TemplateTML>();
		for( File f : tmlDirectory.listFiles() ) {
			if(getFileType(f.getName()).equals("tml")){
				try{
					TemplateTML t = new TemplateTML(f,lw).buildLayout();
					templates.add(t);	
				}catch(Exception e){
					lw.println( "There was a problem loading the .tml file: " + f.getName() );
					e.printStackTrace(lw);
					lw.println();
				}
		}}
		return templates;
	}

	//****************************************  FUNCTION - loadWallStylesFromDir *************************************************************************************//
	public static ArrayList<WallStyle> loadWallStylesFromDir(File stylesDirectory, PrintWriter lw) throws Exception{
		if(!stylesDirectory.exists()) 
			throw new Exception("Could not find directory /"+stylesDirectory.getName()+" in the resource folder "+stylesDirectory.getParent()+"!");
		
		//load buildings
		lw.println("\nLoading building subfolder in "+stylesDirectory+"\\"+BUILDING_DIRECTORY_NAME+"...");
		HashMap<String,TemplateTML> buildingTemplates=new HashMap<String,TemplateTML>();
		try{
			Iterator<TemplateTML> itr=loadTemplatesFromDir(new File(stylesDirectory,BUILDING_DIRECTORY_NAME),lw).iterator();
			while(itr.hasNext()){
				TemplateTML t=itr.next();
				buildingTemplates.put(t.name,t);
			}
		} catch(Exception e){
			lw.println("No buildings folder for "+stylesDirectory.getName()+e.toString());
		}

		for(int i=0;i<10;i++) {}
		
		//load walls
		lw.println("\nLoading wall styles from directory "+stylesDirectory+"...");
			
		ArrayList<WallStyle> styles = new ArrayList<WallStyle>();
		for( File f : stylesDirectory.listFiles() ) {
			if(getFileType(f.getName()).equals("tml")){
				try{
					WallStyle ws=new WallStyle(f,buildingTemplates,lw);
					styles.add(ws);
				}catch(Exception e){
					lw.println( "Error loading wall style: " + f.getName() );
					e.printStackTrace(lw);
					lw.println();
				}
			}
		}
		lw.flush();
		if(styles.size()==0) throw new Exception("Did not find any valid wall styles!");
		return styles;
	}
	
	
	public static WallStyle pickBiomeWeightedWallStyle(ArrayList<WallStyle> styles,World world, int i, int k, Random random, boolean ignoreBiomes){
		//BUKKIT PORT
		//int biome=getBiomeNum(world.getBiome(i,k));
		int biome=getBiomeNum(world.getWorldChunkManager().getBiomeGenAt(i>>4,k>>4));
		if((biome < 0 || biome >= BIOME_NAMES.length) && !ignoreBiomes) return null;
	  	int sum=0;
	  	for(WallStyle ws : styles){
	  		if(ignoreBiomes || ws.Biomes == ALL_BIOMES || ws.Biomes[biome]>0) sum+=ws.weight;
	  	}
	  	if(sum<=0) return null;
	  

	  	int s=random.nextInt(sum);
	  	sum=0;
	  	for(WallStyle ws : styles){
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
	
	public static int getBiomeNum( BiomeGenBase biomeCheck ) {
        //if( biomeCheck == BiomeGenBase.rainforest ) 			return BIOME_RAINFOREST;
        if( biomeCheck == BiomeGenBase.swampland )	 			return BIOME_SWAMPLAND;
       // else if( biomeCheck == BiomeGenBase.seasonalForest ) 	return BIOME_SEASONALFOREST;
        else if( biomeCheck == BiomeGenBase.forest ) 			return BIOME_FOREST;
        //else if( biomeCheck == BiomeGenBase.savanna ) 			return BIOME_SAVANNA;
        //else if( biomeCheck == BiomeGenBase.shrubland ) 		return BIOME_SHRUBLAND;
        else if( biomeCheck == BiomeGenBase.taiga ) 			return BIOME_TAIGA;
		else if( biomeCheck == BiomeGenBase.desert ) 			return BIOME_DESERT;
        else if( biomeCheck == BiomeGenBase.plains) 			return BIOME_PLAINS;   //MP PORT replace field_35485_c with field_35519_b
       // else if( biomeCheck == BiomeGenBase.iceDesert ) 		return BIOME_ICEDESERT;
        //else if( biomeCheck == BiomeGenBase.tundra ) 			return BIOME_TUNDRA;
        else if( biomeCheck == BiomeGenBase.hell ) 				return BIOME_HELL;
        else if( biomeCheck == BiomeGenBase.sky ) 				return BIOME_SKY;
		
		return BIOME_FOREST;
	}
	

	//****************************************  FUNCTION - loadStreets *************************************************************************************//
	public static void loadStreets(ArrayList<WallStyle> cityStyles,File streetsDirectory, PrintWriter lw) throws Exception{
		//streets, don't print error if directory DNE
		HashMap<String,WallStyle> streetTemplateMap=new HashMap<String,WallStyle>();
		Iterator<WallStyle> itr;
		try{
			lw.println("\nLoading streets subfolder in "+streetsDirectory+"...");
			itr=loadWallStylesFromDir(streetsDirectory,lw).iterator();
			while(itr.hasNext()){
				WallStyle cs=itr.next();
				streetTemplateMap.put(cs.name,cs);
			}
		} catch(Exception e){
			lw.println("No street folder for "+streetsDirectory.getName()+e.toString());
		}

		itr=cityStyles.iterator();
		while(itr.hasNext()){
			WallStyle cs=itr.next();
			cs.streets=cs.loadChildStyles("street_templates",streetTemplateMap);
			if(cs.streets.size()==0 && !cs.underground) itr.remove();
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



