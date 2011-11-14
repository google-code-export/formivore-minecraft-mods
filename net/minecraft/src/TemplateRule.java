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
 * TemplateRule reads in a rule String and defines a rule that blocks can be sampled from.
 */

import java.util.Random;

public class TemplateRule {
    public final static int HOLE_ID=-1;
    public final static int[] AIR_BLOCK=new int[]{0,0};
    public final static int[] HOLE_BLOCK=new int[]{HOLE_ID,0};
    public final static int[] PRESERVE_BLOCK=new int[]{Building.PRESERVE_ID,0};
    public final static int[] HARD_SPAWNER_BLOCK=new int[]{Building.HARD_SPAWNER_ID,0};
    public final static int[] PIG_ZOMBIE_SPAWNER_BLOCK=new int[]{Building.PIG_ZOMBIE_SPAWNER_ID,0};
    public final static int[] ENDERMAN_SPAWNER_BLOCK=new int[]{Building.ENDERMAN_SPAWNER_ID,0};
    public final static int[] TOWER_CHEST_BLOCK=new int[]{Building.TOWER_CHEST_ID,0};
    public final static int[] HARD_CHEST_BLOCK=new int[]{Building.HARD_CHEST_ID,0};
    public final static int[] STONE_BLOCK=new int[]{Building.STONE_ID,0};
    public final static int[] PORTAL_BLOCK=new int[]{Building.PORTAL_ID,0};
    public final static int FIXED_FOR_BUILDING=5;
    
    public final static String BLOCK_NOT_REIGSTERED_ERROR_PREFIX="Error reading rule: BlockID ";  //so we can treat this error differently
    
    public final static TemplateRule AIR_RULE=new TemplateRule(AIR_BLOCK);
    
    private int[] blockIDs, blockMDs;
    public int chance = 100, condition = 0;
    public int[] primaryBlock=null;
    private int[] fixedRuleChosen=null;

    public TemplateRule (String rule, BuildingExplorationHandler explorationHandler, boolean checkMetaValue ) throws Exception {
        String[] items = rule.split( "," );
        int numblocks = items.length - 2;
        if( numblocks < 1 ) { throw new Exception( "Error reading rule: No blockIDs specified for rule!" ); }
        condition = Integer.parseInt( items[0].trim() );
        chance = Integer.parseInt( items[1].trim() );
        blockIDs = new int[numblocks];
        blockMDs = new int[numblocks];
        
		String[] data;
        for( int i = 0; i < numblocks; i++ ) {
        	data = items[i + 2].trim().split( "-" );
        	blockIDs[i]=Integer.parseInt( data[0] );
        	if(!Building.isValidRuleBlock(blockIDs[i],explorationHandler)){
        		throw new Exception(BLOCK_NOT_REIGSTERED_ERROR_PREFIX+blockIDs[i]+" not registered!");
        	}
        	blockMDs[i]= data.length>1 ? Integer.parseInt( data[1]) : 0;
        	if(checkMetaValue && !Building.metaValueIsValid(blockIDs[i], blockMDs[i])) {
        		throw new Exception("Error reading rule: Bad meta value for block-meta "+items[i+2]);
        	}
        }
        
        setPrimaryBlock();
    } 

    public TemplateRule(int[] block){
    	blockIDs=new int[]{block[0]};
    	blockMDs=new int[]{block[1]};
    }
    
    public TemplateRule(int[] block, int chance_){
    	blockIDs=new int[]{block[0]};
    	blockMDs=new int[]{block[1]};
    	chance=chance_;
    }
    
    public TemplateRule(int[] blockIDs_, int[] blockMDs_, int chance_){
    	blockIDs=blockIDs_;
    	blockMDs=blockMDs_;
    	chance=chance_;
    }
    
    public void setFixedRule(Random random){
    	if(condition==FIXED_FOR_BUILDING){
	    	int m=random.nextInt(blockIDs.length);
	    	fixedRuleChosen= new int[]{blockIDs[m],blockMDs[m]};
    	}else fixedRuleChosen=null;
    }
    
    public TemplateRule getFixedRule(Random random){
    	if(condition!=FIXED_FOR_BUILDING) return this;
    	
    	int m=random.nextInt(blockIDs.length);
    	return new TemplateRule(new int[]{blockIDs[m],blockMDs[m]},chance);
    }
    
    public int[] getBlock(Random random){
    	if(chance >=100 || random.nextInt(100) < chance){
    		if(fixedRuleChosen!=null) return fixedRuleChosen;
    		
    		int m=random.nextInt(blockIDs.length);
    		return new int[]{blockIDs[m],blockMDs[m]};
    	}
    	return AIR_BLOCK;
    }
    
    public int[] getBlockOrHole(Random random){
    	if(chance >=100 || random.nextInt(100) < chance){
    		if(fixedRuleChosen!=null) return fixedRuleChosen;
    		
    		int m=random.nextInt(blockIDs.length);
    		return new int[]{blockIDs[m],blockMDs[m]};
    	}
    	return HOLE_BLOCK;
    }
    
    public boolean isPreserveRule(){
    	for(int blockID : blockIDs)
    		if(blockID!=Building.PRESERVE_ID ) return false;
    	return true;
    }
    
    public int[] getNonAirBlock(Random random){
    	int m=random.nextInt(blockIDs.length);
    	return new int[]{blockIDs[m],blockMDs[m]};
    }
    
    public int[] getBlockIDs(){
    	return blockIDs;
    }
    
    @Override
    public String toString(){
    	String str=condition +","+chance;
    	for(int m=0; m<blockIDs.length; m++){
    		str+=","+blockIDs[m];
    		if(blockMDs[m]!=0) str+="-"+blockMDs[m];
    	}
    	return str;
    }

    
    //returns the most frequent block in rule
    private void setPrimaryBlock(){
    	int[] hist=new int[blockIDs.length];
    	for(int l=0;l<hist.length;l++)
    		for(int m=0;m<hist.length;m++)
    			if(blockIDs[l]==blockIDs[m]) hist[l]++;
    	
    	int maxFreq=0;
    	for(int l=0;l<hist.length;l++){
    		if(hist[l]>maxFreq){
    			maxFreq=hist[l];
    			primaryBlock=new int[]{blockIDs[l],blockMDs[l]};
    		}
    	}
    }
    

}