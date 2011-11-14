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
 *	Building is a general class for buildings. Classes can inherit from Building to build from a local frame of reference.
 *
 *  Local frame of reference variables:
 *     i,j,k are coordinate inputs for global frame of reference functions.
 *     x,z,y are coordinate inputs for local frame of reference functions. 
 *     axY and EW determine the Y axis. E.g. EW=0,axY=-1 => Y-axis points north.
 *     axXHand =-1,1 determines whether X-axis points left or right respectively when facing along Y-axis.
 *
 *			         (EW=0,axY=-1,dir=-2)
 *							(-i)
 *                            n
 *                            n
 *  (EW=1,axY=1,dir=1) (+k)www*eee(-k)  (EW=1,axY=-1,dir=-1)
 *                            s
 *                            s
 *							(+i)
 *                    (EW=0,axY=1,dir=2)
 *
 *
 *
 *			       axY=1
 *
 *                   ^
 *                   |
 *        axX=-1   <-*->  axX=1
 *                   |
 *                   v
 *
 *                axY=-1
*/
		

import java.util.Random;
import java.lang.Math;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;

//BUKKIT PORT
/*
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Sign;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.Inventory;
//import org.bukkit.inventory.ItemStack;
*/
//replace world.getBlockId( with world.getBlockTypeIdAt(
//replace world.setBlock( with world.getBlockAt().setTypeId(
//replace world.setBlockAndMetadata( with world.getBlockAt().setTypeIdAndData(

public class Building
{
		public final static int HIT_WATER=-666;
		public final static int EASY_CHEST=0,MEDIUM_CHEST=1,HARD_CHEST=2,TOWER_CHEST=3;
		public final static int DIR_WEST=1,DIR_EAST=-1,DIR_SOUTH=2,DIR_NORTH=-2;
		public final static int DIR_WEST_EAST=1, DIR_SOUTH_NORTH=0;
		public final static int ROT_R=1,ROT_L=-1,ROT_180=2;
		public final static int R_HAND=1,L_HAND=-1;
		public final static int WORLD_HEIGHT=128;
	
		//**** WORKING VARIABLES **** 
		protected World world;
		protected Random random;
		protected TemplateRule bRule; //main structural blocktype
		public int bWidth, bHeight, bLength;
		public int bID; //Building ID number
		private LinkedList<int[]> delayedBuildQueue;
		protected WorldGeneratorThread wgt; 

		protected int i1, j1, k1; //origin coordinates. The child class may want to move the origin as it progress to use as a "cursor" position.
		protected int EW,NS; //one of these should be 1, the other 0
		protected int axY,axX; //record whether the Y-axis(+-k) and the X-axis (+-i) point in the positive or negative directions.
		protected int bHand; //hand of secondary axis. Takes values of 1 for right-handed, -1 for left-handed.
		protected int bDir; //Direction code of primary axis. Takes values of DIR_WEST=1,DIR_EAST=-1,DIR_SOUTH=2,DIR_NORTH=-2.


	//****************************  CONSTRUCTORS - Building  *************************************************************************************//
	public Building(int ID_, WorldGeneratorThread wgt_,TemplateRule buildingRule_,int dir_,int axXHand_, int[] dim, int[] sourcePt) {
		bID=ID_;
		wgt=wgt_;
		world=wgt.world;
		bRule=buildingRule_;
		bWidth=dim[0];
		bHeight=dim[1];
		bLength=dim[2];
		random=wgt.random;
		bHand=axXHand_;
		setPrimaryAx(dir_);
		if(sourcePt!=null && sourcePt.length==3){
			setOrigin(sourcePt[0],sourcePt[1],sourcePt[2]);
		}
		delayedBuildQueue=new LinkedList<int[]>();
	}
    
    //******************** ORIENTATION FUNCTIONS *************************************************************************************************************//
    
	//picks a random direction
    public final static int pickDir(Random random){
    	return (2*random.nextInt(2)-1)*(random.nextInt(2)+1);
    }
  	
  	public void setPrimaryAx(int dir_){
  		bDir=dir_;
  		switch(bDir){
	  		case DIR_WEST: EW=1; axY=1; break;
	  		case DIR_EAST: EW=1; axY=-1; break;
	  		case DIR_SOUTH: EW=0; axY=1; break;
	  		case DIR_NORTH: EW=0; axY=-1; break;
  		}
  		NS=1-EW;
  		axX=axY*bHand*(NS-EW);
  		if(!(axY==-1 || axY==1)) System.err.println("ERROR: Y-axis must be -1 or 1! axY="+axY+" dir="+bDir);
			if(!(axX==-1 || axX==1)) System.err.println("ERROR: X-axis must be -1 or 1! axX="+axX+" dir="+bDir);
			if(!(EW==1 || EW==0)) System.err.println("ERROR: EW must be 1 or 0! dir="+bDir);
  	}
    
    public final static int rotateDir(int dir,int rotation){
    	switch(rotation){
  		case ROT_R:
  			if(dir==DIR_NORTH) return DIR_EAST;
  			if(dir==DIR_EAST) return DIR_SOUTH;
  			if(dir==DIR_SOUTH) return DIR_WEST;
  			if(dir==DIR_WEST) return DIR_NORTH;
  		case ROT_L:
  			if(dir==DIR_NORTH) return DIR_WEST;
  			if(dir==DIR_EAST) return DIR_NORTH;
  			if(dir==DIR_SOUTH) return DIR_EAST;
  			if(dir==DIR_WEST) return DIR_SOUTH;
  		case ROT_180: return -dir;
    	}
    	return dir;
    }
    
    //outputs metadir rotated to this Building's orientation and handedness
    //metaDir input should be the direction desired if dir==DIR_NORTH and axXHand=R_HAND
    public int rotateMetaDirToLocalAxes(int metaDir){
    	if( metaDir==DIR_NORTH  || metaDir==DIR_SOUTH)
    		return EW==0 ? -axY*metaDir : -axY*metaDir/2;
    	return EW==0 ? -axY*bHand*metaDir : axY*bHand*metaDir*2;
    }
    
    protected final void setOrigin(int i1_,int j1_, int k1_){
		i1=i1_;
		j1=j1_;
		k1=k1_;
    }
    
    protected final void shiftOrigin(int gradX, int gradZ, int gradY){
		i1+=EW*axX*gradX + NS*axY*gradY;
		j1+=gradZ;
		k1+=NS*axX*gradX + EW*axY*gradY;
	}
  	
  //******************** LOCAL COORDINATE FUNCTIONS - ACCESSORS *************************************************************************************************************//
    //Use these instead of World.java functions when to build from a local reference frame
    //when i1,j1,k1 are set to working values.
    
    public final int getI(int x, int y){
		return i1+ EW*axX*x+NS*axY*y;
    }
    
    public final int getJ(int z){
		return j1+z;
    }
    
    public final int getK(int x, int y){
		return k1+EW*axY*y+NS*axX*x;
    }
    
    public final int[] getIJKPt(int x, int z, int y){
    	int[] pt=new int[3];
    	pt[0]=i1+ EW*axX*x+NS*axY*y;
    	pt[1]=j1+z;
    	pt[2]=k1+EW*axY*y+NS*axX*x;
    	//if(pt[0] < 0xfe17b800 || pt[2] < 0xfe17b800 || pt[0] >= 0x1e84800 || pt[2] > 0x1e84800 ||pt[1] < 0 || pt[1] >= 128)
        //    return BAD_COORDINATES_PT;     
    	return pt;
    }
    
    public final int[] getSurfaceIJKPt(int x, int y, int j, boolean waterIsSurface, boolean wallIsSurface){
    	int[] pt=getIJKPt(x,0,y);
    	pt[1]=findSurfaceJ(world, pt[0], pt[2],j, waterIsSurface, wallIsSurface);
    	return pt;
    }
    
    public final int getX(int[] pt){
		return axX*(EW*(pt[0]-i1)+NS*(pt[2]-k1));
    }
    
    public final int getZ(int[] pt){
		return pt[1]-j1;
    }
    
    public final int getY(int[] pt){
		return axY*(NS*(pt[0]-i1)+EW*(pt[2]-k1));
    }
    
    protected final boolean queryExplorationHandler(int x, int z, int y) throws InterruptedException{
    	return wgt.queryExplorationHandler(getI(x,y),getK(x,y));
    }
    
    protected final int getBlockIdLocal(int x, int z, int y){
    	if(bDir==DIR_NORTH) return (axX==1 ? world.getBlockId(i1-y,j1+z,k1+x) : world.getBlockId(i1-y, j1+z,k1-x));
  		if(bDir==DIR_EAST) return  (axX==1 ? world.getBlockId(i1+x,j1+z,k1-y) : world.getBlockId(i1-x, j1+z,k1-y));
  		if(bDir==DIR_SOUTH) return (axX==1 ? world.getBlockId(i1+y,j1+z,k1+x) : world.getBlockId(i1+y, j1+z,k1-x));
  		return (axX==1 ? world.getBlockId(i1+x,j1+z,k1+y) : world.getBlockId(i1-x, j1+z,k1+y));
    }
    
  //******************** LOCAL COORDINATE FUNCTIONS - SET BLOCK FUNCTIONS *************************************************************************************************************//
    protected final void setBlockLocal(int x, int z, int y, int blockID){
    	if(blockID>=SPECIAL_BLOCKID_START) { setSpecialBlockLocal(x,z,y,blockID,0); return; }
    	
    	int[] pt=getIJKPt(x,z,y);
    	if(blockID==AIR_ID && world.getBlockId(pt[0], pt[1], pt[2])==AIR_ID) return;
    	emptyIfChest(pt);

    	if(randLightingHash[(x & 0x7) | (y & 0x38) | (z & 0x1c0)])
    		world.setBlock(pt[0],pt[1],pt[2],blockID);
    	else setBlockNoLighting(world,pt[0],pt[1],pt[2],blockID);
    }
    
    protected final void setBlockAndMetadataLocal(int x, int z, int y, int blockID, int metadata){
     	if(blockID>=SPECIAL_BLOCKID_START) { setSpecialBlockLocal(x,z,y,blockID,metadata); return; }
     	if(blockID==TemplateRule.HOLE_ID) blockID=AIR_ID;
     	
    	int[] pt=getIJKPt(x,z,y);
    	if(blockID==AIR_ID && world.getBlockId(pt[0], pt[1], pt[2])==AIR_ID) return;
    	if(blockID!=CHEST_ID) emptyIfChest(pt);
    	if(IS_DELAY_BLOCK[blockID]) delayedBuildQueue.offer(new int[]{pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata)});
    	else{
    		if(randLightingHash[(x & 0x7) | (y & 0x38) | (z & 0x1c0)])
    			world.setBlockAndMetadata(pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata));
    		else setBlockAndMetaNoLighting(world,pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata));
    	}
    }
    
    protected final void setBlockAndMetadataLocal(int x, int z, int y, TemplateRule rule){
    	int [] idAndMeta=rule.getBlock(random);
    	if(idAndMeta[0]==TemplateRule.HOLE_ID) idAndMeta[0]=AIR_ID;
     	if(idAndMeta[0]>=SPECIAL_BLOCKID_START) { setSpecialBlockLocal(x,z,y,idAndMeta[0],idAndMeta[1]); return; } 	
     	
    	int[] pt=getIJKPt(x,z,y);
    	if(idAndMeta[0]==AIR_ID && world.getBlockId(pt[0], pt[1], pt[2])==AIR_ID) return;
    	if(idAndMeta[0]!=CHEST_ID) emptyIfChest(pt);
    	if(IS_DELAY_BLOCK[idAndMeta[0]]) delayedBuildQueue.offer(new int[]{pt[0],pt[1],pt[2],idAndMeta[0],rotateMetadata(idAndMeta[0],idAndMeta[1])});
    	else{
    		if(randLightingHash[(x & 0x7) | (y & 0x38) | (z & 0x1c0)])
    			world.setBlockAndMetadata(pt[0],pt[1],pt[2],idAndMeta[0],rotateMetadata(idAndMeta[0],idAndMeta[1]));
    		else setBlockAndMetaNoLighting(world,pt[0],pt[1],pt[2],idAndMeta[0],rotateMetadata(idAndMeta[0],idAndMeta[1]));
    	}
    }
     
    //allows control of lighting. Also will build even if replacing air with air.
    protected final void setBlockAndMetadataLocal(int x, int z, int y, int blockID, int metadata, boolean lighting){
    	if(blockID>=SPECIAL_BLOCKID_START) { setSpecialBlockLocal(x,z,y,blockID,metadata); return; }
     	if(blockID==TemplateRule.HOLE_ID) blockID=AIR_ID;
     	
    	int[] pt=getIJKPt(x,z,y);
    	if(blockID!=CHEST_ID) emptyIfChest(pt);
    	if(IS_DELAY_BLOCK[blockID]) delayedBuildQueue.offer(new int[]{pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata)});
    	else{
    		if(lighting)
    			world.setBlockAndMetadata(pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata));
    		else setBlockAndMetaNoLighting(world,pt[0],pt[1],pt[2],blockID,rotateMetadata(blockID,metadata));
    	}
    }
    
    protected final void flushDelayed(){
    	while(delayedBuildQueue.size()>0){
    		int[] block=(delayedBuildQueue.poll());
    		if(block[3]==PAINTING_SPECIAL_ID) setPainting(block, block[4]);
    		else if(IS_HUMANS_PLUS_FLAG[block[3]]) setHumansPlusFactionFlag(block, block[3],block[4]);
    		else if(block[3]!=TORCH_ID || Block.torchWood.canPlaceBlockAt(world,block[0],block[1],block[2]))
    			world.setBlockAndMetadata(block[0],block[1],block[2],block[3],block[4]);
    		//if(block[3]==TORCH_ID)
    		//	wgt.explorationHandler.queueLighting(new int[]{block[0],block[1],block[2],block[0],block[1],block[2]});
    	}
    }
  
  //******************** LOCAL COORDINATE FUNCTIONS - SPECIAL BLOCK FUNCTIONS *************************************************************************************************************//
 
  //&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setSpecialBlockLocal &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
    protected final void setSpecialBlockLocal(int x, int z, int y, int blockID, int metadata){
    	if(blockID==PRESERVE_ID) return; // preserve existing world block
    	int[] pt=getIJKPt(x,z,y);
    	
    	switch(blockID) {
			case ZOMBIE_SPAWNER_ID: setMobSpawner(pt,1,0); return;
			case SKELETON_SPAWNER_ID: setMobSpawner(pt,1,1); return;
			case SPIDER_SPAWNER_ID: setMobSpawner(pt,1,2); return;
			case CREEPER_SPAWNER_ID: setMobSpawner(pt,1,3); return;
			case UPRIGHT_SPAWNER_ID: if(random.nextInt(3)==0) setMobSpawner(pt,1,3); else setMobSpawner(pt,2,0); return;
			case EASY_SPAWNER_ID: setMobSpawner(pt,2,0); return; 
			case MEDIUM_SPAWNER_ID: setMobSpawner(pt,3,0); return; 
			case HARD_SPAWNER_ID: setMobSpawner(pt,4,0); return; 
			case EASY_CHEST_ID: setLootChest(pt,EASY_CHEST); return;
			case MEDIUM_CHEST_ID: setLootChest(pt,MEDIUM_CHEST); return;
			case HARD_CHEST_ID: setLootChest(pt,HARD_CHEST); return;
			case TOWER_CHEST_ID: setLootChest(pt,TOWER_CHEST); return;
			case PIG_ZOMBIE_SPAWNER_ID: setMobSpawner(pt,1,4); return;
			case ENDERMAN_SPAWNER_ID: setMobSpawner(pt,1,6); return;
			case CAVE_SPIDER_SPAWNER_ID: setMobSpawner(pt,1,7); return;
			case GHAST_SPAWNER_ID: setMobSpawner(pt,1,5); return;
			case WALL_STAIR_ID: world.setBlockAndMetadata(pt[0],pt[1],pt[2],STEP_ID,rotateMetadata(STEP_ID,metadata)); return; //this case should not be reached
			case PAINTING_SPECIAL_ID: delayedBuildQueue.offer(new int[]{pt[0],pt[1],pt[2],blockID,metadata}); return;
    	}
    	if(IS_HUMANS_PLUS_FLAG[blockID]) delayedBuildQueue.offer(new int[]{pt[0],pt[1],pt[2],blockID,metadata});
    }
    
    //&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setMobSpawner &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
    private final void setMobSpawner(int[] pt, int nTypes, int offset){
    	String mob="";
        int n = random.nextInt(nTypes)+offset;
        switch(n) {
        	case 0: mob="Zombie"; break;
        	case 1: mob="Skeleton"; break;
        	case 2: mob="Spider"; break;
        	case 3: mob="Creeper"; break;
        	case 4: mob="PigZombie"; break;
        	case 5: mob="Ghast"; break;
        	case 6: mob="Enderman"; break;
        	case 7: mob="CaveSpider"; break;
        	default: mob="Skeleton"; break;
		} 
        world.setBlock(pt[0],pt[1],pt[2],MOB_SPAWNER_ID);
        
        //BUKKIT PORT
        // ((CreatureSpanwer)world.getBlockAt(pt[0],pt[1],pt[2]).getState()).setCreatureTypeId(mob);
        try{ //sometimes get an array out of bound error from minecraft (?!)
	        TileEntityMobSpawner tileentitymobspawner=( TileEntityMobSpawner)world.getBlockTileEntity(pt[0],pt[1],pt[2]);
	        if(tileentitymobspawner!=null) tileentitymobspawner.setMobID(mob);
        } catch(Exception e) { System.out.println("Error placing mob spawner: "+e.getMessage());} 
 	}
    
  //&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setLootChest &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
 	private final void setLootChest(int[] pt,int chestType){
 		try{
 			world.setBlock(pt[0],pt[1],pt[2],CHEST_ID);
        
 			//BUKKIT PORT
 			//Inventory inv=((Chest)world.getBlockAt(pt[0],pt[1],pt[2]).getState()).getInventory();
 			//sometimes get an array out of bound error from minecraft (?!)
	        TileEntityChest tileentitychest=(TileEntityChest)world.getBlockTileEntity(pt[0],pt[1],pt[2]);
	 		
		    for(int m=0; m<wgt.chestTries[chestType]; m++){
		    	if(random.nextBoolean()){
			    	ItemStack itemstack=getChestItemstack(chestType);
			    	
			    	//BUKKIT PORT
			    	//inv.setItem(random.nextInt(inv.getSize()), itemstack);
			        if(itemstack != null && tileentitychest!=null)
			        	tileentitychest.setInventorySlotContents(random.nextInt(tileentitychest.getSizeInventory()), itemstack);
		    	}
		    }
        } catch(Exception e) { 
        	System.out.println("Error placing loot chest: "+e.getMessage());
        	e.printStackTrace();
        }
    }
 	
 	private ItemStack getChestItemstack(int chestType){
 		if(chestType==TOWER_CHEST && random.nextInt(4)==0){ //for tower chests, chance of returning the tower block
 			return new ItemStack(bRule.primaryBlock[0],random.nextInt(10),bRule.primaryBlock[1]);
 		}
 		int[][] itempool=wgt.chestItems[chestType];
 		int idx=selectWeightedOption(random,itempool[3],itempool[0]);
 		return new ItemStack(itempool[1][idx], 
 							 itempool[4][idx] + random.nextInt(itempool[5][idx]-itempool[4][idx]+1), 
 							 itempool[2][idx]);
 	}
 	
 	//&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setSignOrPost &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
 	public void setSignOrPost(int x2,int z2,int y2,boolean post,int sDir,String[] lines){
 		int[] pt=getIJKPt(x2,z2,y2);
 		world.setBlockAndMetadata(pt[0],pt[1],pt[2],post ? SIGN_POST_ID : WALL_SIGN_ID,sDir);
 		
 		//BUKKIT PORT
 		//Sign sign=((Sign)world.getBlockAt(pt[0],pt[1],pt[2]).getState());
 		TileEntitySign tileentitysign=(TileEntitySign)world.getBlockTileEntity(pt[0],pt[1],pt[2]);
 		if(tileentitysign==null) return;
 		
 		for(int m=0;m<Math.min(lines.length, 4);m++){
 				//BUKKIT PORT
 				//sign.setLine(m, lines[m]);
 				tileentitysign.signText[m]=lines[m];
 	    }
 	}
 	
 	//&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setPainting &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
 	//MP PORT
 	//public void setPainting(int[] pt, int metadata){}
 	
 	public void setPainting(int[] pt, int metadata){
 		//painting uses same orientation meta as ladders.
 		//Have to adjust ijk since unlike ladders the entity exists at the block it is hung on.
 		int dir=rotateMetaDirToLocalAxes(LADDER_META_TO_DIR[metadata]);
 		if(dir==DIR_NORTH || dir==DIR_SOUTH) pt[0]+=dir/2; else pt[2]+=dir;
 			
 		EntityPainting entitypainting = new EntityPainting(world,pt[0],pt[1],pt[2],PAINTING_DIR_TO_FACEDIR[dir+2]);
        if(entitypainting.canStay() && !world.multiplayerWorld)
            world.entityJoinedWorld(entitypainting);
        
 	}
 	
 	//&&&&&&&&&&&&&&&&& SPECIAL BLOCK FUNCTION - setPainting &&&&&&&&&&&&&&&&&&&&&&&&&&&&&//
 	//MP PORT
 	//public void setHumansPlusFactionFlag(int[] pt, int blockID, int metadata){}
    public void setHumansPlusFactionFlag(int[] pt, int blockID, int metadata){
    	if(!wgt.master.humansPlusLoaded) return;
    	
    	int dir=rotateMetaDirToLocalAxes(LADDER_META_TO_DIR[metadata]);
    	if(dir==DIR_NORTH || dir==DIR_SOUTH) pt[0]+=dir/2; else pt[2]+=dir;
    	Integer faceDir=PAINTING_DIR_TO_FACEDIR[dir+2]; 
    	
    	try {
    		//call constructor using reflection
    		Object arglist[] = new Object[]{world,new Integer(pt[0]), new Integer(pt[1]), new Integer(pt[2]),faceDir};
    		Object h_EntityFlagObj = wgt.master.h_EntityFlagConstr.newInstance(arglist);
    		Object retobj = wgt.master.updateFlagMethod.invoke(h_EntityFlagObj,new Object[]{});
    		if(((Boolean)retobj).booleanValue()){
    			if(!world.multiplayerWorld){
    				world.entityJoinedWorld((Entity)h_EntityFlagObj);
    			}
    		}
    		
    		//now change the "flag" enum field to the desired faction
    		Object factionObj=null;
    		switch(blockID){
	    		case HUMANS_PLUS_ASSASIN_FLAG_ID: factionObj=wgt.master.enumAssassinObj; break;
	    		case HUMANS_PLUS_ROGUE_FLAG_ID: factionObj=wgt.master.enumRogueObj; break;
	    		case HUMANS_PLUS_BANDIT_FLAG_ID: factionObj=wgt.master.enumBanditObj; break;
	    		case HUMANS_PLUS_PEACEFUL_FLAG_ID: factionObj=wgt.master.enumPeacefulObj; break;
	    		case HUMANS_PLUS_MILITIA_FLAG_ID: factionObj=wgt.master.enumMilitiaObj; break;
	    		case HUMANS_PLUS_SHADOW_FLAG_ID: factionObj=wgt.master.enumShadowObj; break;
    		}
    		if(factionObj!=null)
    			wgt.master.h_EntityFlagFlagfFld.set(h_EntityFlagObj, factionObj);
    	}
    	catch (Throwable e) {
    		System.err.println(e);
    	}
    	
    }
 	
 	/*
    protected final boolean isBlock(int x, int z, int y, int[] blockTypes){
    	int blkId=getBlockIdLocal(x,z,y);
    	for(int blockType : blockTypes)
    		if(blkId==blockType)
    			return true;
	  	return false;
	  }
	 */
    
  //******************** LOCAL COORDINATE FUNCTIONS - BLOCK TEST FUNCTIONS *************************************************************************************************************//
    protected final boolean isWallable(int x, int z, int y){
    	if(bDir==DIR_NORTH) return (axX==1 ? IS_WALLABLE[world.getBlockId(i1-y,j1+z,k1+x)] : IS_WALLABLE[world.getBlockId(i1-y, j1+z,k1-x)]);
  		if(bDir==DIR_EAST) return  (axX==1 ? IS_WALLABLE[world.getBlockId(i1+x,j1+z,k1-y)] : IS_WALLABLE[world.getBlockId(i1-x, j1+z,k1-y)]);
  		if(bDir==DIR_SOUTH) return (axX==1 ? IS_WALLABLE[world.getBlockId(i1+y,j1+z,k1+x)] : IS_WALLABLE[world.getBlockId(i1+y, j1+z,k1-x)]);
  		return (axX==1 ? IS_WALLABLE[world.getBlockId(i1+x,j1+z,k1+y)] : IS_WALLABLE[world.getBlockId(i1-x, j1+z,k1+y)]);
	  }
	  
	 protected final boolean isWallableIJK(int pt[]){
	  	if(pt==null) return false;
	  	return IS_WALLABLE[world.getBlockId(pt[0], pt[1], pt[2])];

	 }
	  
	 protected final boolean isWallBlock(int x, int z, int y){
		if(bDir==DIR_NORTH) return (axX==1 ? IS_WALL_BLOCK[world.getBlockId(i1-y,j1+z,k1+x)] : IS_WALL_BLOCK[world.getBlockId(i1-y, j1+z,k1-x)]);
  		if(bDir==DIR_EAST) return  (axX==1 ? IS_WALL_BLOCK[world.getBlockId(i1+x,j1+z,k1-y)] : IS_WALL_BLOCK[world.getBlockId(i1-x, j1+z,k1-y)]);
  		if(bDir==DIR_SOUTH) return (axX==1 ? IS_WALL_BLOCK[world.getBlockId(i1+y,j1+z,k1+x)] : IS_WALL_BLOCK[world.getBlockId(i1+y, j1+z,k1-x)]);
  		return (axX==1 ? IS_WALL_BLOCK[world.getBlockId(i1+x,j1+z,k1+y)] : IS_WALL_BLOCK[world.getBlockId(i1-x, j1+z,k1+y)]);
    }
	 
	protected final boolean isArtificialWallBlock(int x, int z, int y){
		int blockId=getBlockIdLocal(x,z,y);
		return IS_WALL_BLOCK[blockId] && !(blockId==SANDSTONE_ID && (getBlockIdLocal(x,z+1,y)==SAND_ID || getBlockIdLocal(x,z+2,y)==SAND_ID));
	}
    
    protected final boolean isStairBlock(int x, int z, int y){
	  	int blkId=getBlockIdLocal(x,z,y);
    	return (blkId==STEP_ID || blkId==COBBLESTONE_STAIRS_ID || blkId==WOOD_STAIRS_ID);
    }
    
  //true if block is air, block below is wall block
    protected final boolean isDoorway(int x, int z, int y){
    	return isFloor(x,z,y) && (isWallBlock(x+1,z,y) && isWallBlock(x-1,z,y) || isWallBlock(x,z,y+1) && isWallBlock(x-1,z,y-1));
    }
    
  //true if block is air, block below is wall block
    protected final boolean isFloor(int x, int z, int y){
    	int blkId1=getBlockIdLocal(x,z,y), blkId2=getBlockIdLocal(x,z-1,y);
    	//return ((blkId1==0 || blkId1==STEP_ID) && IS_WALL_BLOCK[blkId2] && blkId2!=LADDER_ID);
    	return ((blkId1==0) && IS_WALL_BLOCK[blkId2] && blkId2!=LADDER_ID);
    }
    
    
  //******************** LOCAL COORDINATE FUNCTIONS - HELPER FUNCTIONS *************************************************************************************************************//
    private final void emptyIfChest(int[] pt){
    	//if block is a chest empty it
    	if(pt!=null && world.getBlockId(pt[0],pt[1],pt[2])==CHEST_ID){
    		
    		//BUKKIT PORT
    		//((Chest)world.getBlockAt(pt[0],pt[1],pt[2]).getState()).getInventory().clear();
    		TileEntityChest tileentitychest=(TileEntityChest)world.getBlockTileEntity(pt[0],pt[1], pt[2]);
    		for(int m=0;m<tileentitychest.getSizeInventory();m++)  tileentitychest.setInventorySlotContents(m,null);
	    }
    }
    
    
    public final String globalCoordString(int x, int z, int y){
    	if(EW==1) return "("+(i1+axX*x)+","+(j1+z)+","+(k1+axY*y)+")";
    	return "("+(i1+axY*y)+","+(j1+z)+","+(k1+axX*x)+")";
    }
    
    //replaces orientationString
    protected final String IDString(){
    	String str="ID="+bID+" axes(Y,X)=";
    	switch(bDir){
    		case DIR_SOUTH: return str + "(S," + (axX>0 ? "W)" : "E)");
    		case DIR_NORTH: return str+ "(N," + (axX>0 ? "W)" : "E)");
    		case DIR_WEST: return str+ "(W," + (axX>0 ? "S)" : "N)");
    		case DIR_EAST: return str + "(E," + (axX>0 ? "S)" : "N)");
    	}
    	return "Error - bad dir value for ID="+bID;
    }
   
   protected final static void fillDown(int[] lowPt, int jtop, World world){
	   while(IS_WALL_BLOCK[world.getBlockId(lowPt[0],lowPt[1],lowPt[2])]) lowPt[1]--;

	   int oldSurfaceBlockId=world.getBlockId(lowPt[0], lowPt[1], lowPt[2]);
	   if(IS_ORE_BLOCK[oldSurfaceBlockId]) oldSurfaceBlockId=STONE_ID;
	   if(oldSurfaceBlockId==DIRT_ID || (lowPt[1] < WORLD_HEIGHT/2 && oldSurfaceBlockId==SAND_ID))
		   oldSurfaceBlockId=GRASS_ID;
	   if(oldSurfaceBlockId==0) oldSurfaceBlockId= Building.isNether(world) ? NETHERRACK_ID : GRASS_ID;
	   int fillBlockId=oldSurfaceBlockId==GRASS_ID ? DIRT_ID : oldSurfaceBlockId;
		
	   for(; lowPt[1]<=jtop; lowPt[1]++)
		   setBlockNoLighting(world,lowPt[0],lowPt[1],lowPt[2], lowPt[1]==jtop ? oldSurfaceBlockId: fillBlockId);
   }
    
   //call with z=start of builDown, will buildDown a maximum of maxDepth blocks + foundationDepth.
   //if buildDown column is completely air, instead buildDown reserveDepth blocks.
   protected final void buildDown(int x, int z, int y, TemplateRule buildRule, int maxDepth, int foundationDepth, int reserveDepth){
	   int stopZ;
	   for(stopZ=z; stopZ>z-maxDepth; stopZ--){
		   if(!isWallable(x,stopZ,y)) break; //find ground height
	   }
	   
	   if(stopZ==z-maxDepth && isWallable(x,z-maxDepth,y)) //if we never hit ground
		   stopZ=z-reserveDepth;
	   else stopZ-=foundationDepth;
	    	
	   
	   for(int z1=z; z1>stopZ; z1--){
		   	int[] idAndMeta=buildRule.getBlock(random);
		   	setBlockAndMetadataLocal(x,z1,y,idAndMeta[0],idAndMeta[1],false);
		}
   }
   
   protected boolean isObstructedFrame(int zstart,int ybuffer){
   	for(int z1=zstart; z1<bHeight; z1++){
   		//for(int x1=0; x1<length; x1++) for(int y1=ybuffer; y1<width-1;y1++)
   		//	if(isWallBlock(x1,z1,y1))
   		//		return true;
   		
   		for(int x1=0; x1<bWidth; x1++)
   			if(isWallBlock(x1,z1,bLength-1)) return true;
   		for(int y1=ybuffer; y1<bLength-1;y1++){
   			if(isWallBlock(0,z1,y1)) return true;
   			if(isWallBlock(bWidth-1,z1,y1)) return true;
   		}
   	}
   	return false;
   }
   
   protected boolean isObstructedSolid(int pt1[],int pt2[]){
   	for(int x1=pt1[0]; x1<=pt2[0]; x1++)
   		for(int z1=pt1[1]; z1<=pt2[1]; z1++)
   			for(int y1=pt1[2]; y1<=pt2[2]; y1++)
				if(!isWallable(x1,z1,y1)) return true;
   	return false;
   }
    
	  
   //******************** STATIC FUNCTIONS ******************************************************************************************************************************************//
   
   public static void setBlockNoLighting(World world, int i, int j, int k, int blockId){
       if(i < 0xfe363c80 || k < 0xfe363c80 || i >= 0x1c9c380 || k >= 0x1c9c380 || j < 0 || j >= 128)
           return;
       
       world.getChunkFromChunkCoords(i >> 4, k >> 4).setBlockID(i & 0xf, j, k & 0xf, blockId);
   }
   
   public static void setBlockAndMetaNoLighting(World world, int i, int j, int k, int blockId, int meta){
	   if(i < 0xfe363c80 || k < 0xfe363c80 || i >= 0x1c9c380 || k >= 0x1c9c380 || j < 0 || j >= 128)
           return;

       world.getChunkFromChunkCoords(i >> 4, k >> 4).setBlockIDWithMetadata(i & 0xf, j, k & 0xf, blockId, meta);
   }
   
    //wiggle allows for some leeway before nonzero is detected
    protected final static int signum(int n,int wiggle){
    	if(n<=wiggle && -n<=wiggle) return 0;
    	return n < 0 ? -1 : 1;
    }
    
    protected final static int signum(int n){
    	return n==0 ? 0 : (n < 0 ? -1 : 1);
    }
    
    public static int distance(int[] pt1, int[] pt2){
    	return (int)Math.sqrt((double)((pt1[0]-pt2[0])*(pt1[0]-pt2[0]) + (pt1[1]-pt2[1])*(pt1[1]-pt2[1]) + (pt1[2]-pt2[2])*(pt1[2]-pt2[2])));
    }
    
	public static boolean isNether(World world){
		//BUKKIT PORT
		//return world.getBiome(0,0)==Biome.HELL;
		//MP PORT
		//return world.worldProvider.isHellWorld;
		return world.worldProvider.isNether;
	}
	
	public static long getWorldCode(World world){
		//BUKKIT PORT
		//return world.getUID().getLeastSignificantBits();
		return world.worldInfo.getRandomSeed() + world.worldProvider.worldType;
	}
    
	//****************************************  CONSTRUCTOR - findSurfaceJ *************************************************************************************//
	//Finds a surface block.
	//Depending on the value of waterIsSurface and wallIsSurface will treat liquid and wall blocks as either solid or air.
    public static int findSurfaceJ(World world, int i, int k, int jinit, boolean waterIsSurface, boolean wallIsSurface ){
    	int blockId;
		if( isNether(world) ) {
			if( (i%2==1) ^ (k%2==1) ) {
				for( int j = 127; j > -1; j-- ) {
					if( world.getBlockId(i,j,k) == 0 ) 
						for( ; j > -1; j-- ) 
							if( !IS_WALLABLE[world.getBlockId(i,j,k)] )
								return j;
				}
			}else {
				for( int j=0; j<128; j++) 
					if( world.getBlockId(i,j,k )==0 )
								return j;
			}
			return -1;
		} 
		else{
			for( int j = jinit; j > -1; j-- ){
				blockId=world.getBlockId(i,j,k);
				if(!IS_WALLABLE[blockId] && (wallIsSurface || !IS_WALL_BLOCK[blockId])) 
					return j;
				if(waterIsSurface && IS_LIQUID_BLOCK[blockId]) return HIT_WATER;
			}
		}
		return -1;
    }
	  
	  public static int selectWeightedOption( Random random, int[] weights, int[] options){
	  	int sum=0, n;
	  	for(n=0;n<weights.length;n++) sum+=weights[n];
	  	if(sum<=0) {
	  		System.out.println("Error selecting options, weightsum not positive!");
	  		return options[0]; //default to returning first option
	  	}
	  	int s=random.nextInt(sum);
	  	sum=0;
	  	n=0;
	  	while(n<weights.length){
	  		sum+=weights[n];
	  		if(sum>s) return options[n];
	  		n++;
	  	}
	  	return options[options.length-1];
	  }
	  /*
	  private static ItemStack makeItemStack(int ID){
		  //BUKKIT PORT
		  //return new ItemStack(ID);
		  
		  return new ItemStack(ID,1,0);
	  }
	  
	  private static ItemStack makeItemStack(int ID, int count){
		  //BUKKIT PORT
		  //return new ItemStack(ID,count);
		  
		  return new ItemStack(ID,count,0);
	  }
	  */


	  /*
	  private ItemStack getTowerStack(){
	      int n = random.nextInt(30);
	      switch(n) {
	      	case 0: case 1: return makeItemStack(bRule==null ? COBBLESTONE_ID : bRule.primaryBlock[0], random.nextInt(8)+1);
	      	case 2: case 3: return makeItemStack(SHEARS_ID);
	      	case 4: 		return makeItemStack(GOLD_HELMET_ID);
	      	case 5: 		return makeItemStack(ARROW_ID, random.nextInt(10) + 1);
	      	case 6: 		return makeItemStack(IRON_INGOT_ID, random.nextInt(2) + 1);
	      	case 7: 		return makeItemStack(STONE_SWORD_ID);
	      	case 8: 		return makeItemStack(IRON_AXE_ID);
	      	case 9: 		return makeItemStack(ICE_ID, random.nextInt(8) + 8);
	      	case 10: 		return makeItemStack(SADDLE_ID);
	      	case 11: case 12: return makeItemStack(WHEAT_ID, random.nextInt(4) + 1);
	      	case 13: 		return makeItemStack(SULPHUR_ID, random.nextInt(4) + 1);
	      	case 14: 		return makeItemStack(LEATHER_CHESTPLATE_ID);
	      	case 15: 		return makeItemStack(PUMPKIN_ID, random.nextInt(4)+1);
	      	case 16: 		return makeItemStack(RAW_FISH_ID, 1);
	      }
	      return null;
	    }
	  
    private ItemStack getLootStack() {
        int rand = random.nextInt( 25 );
        switch( rand ) {
        case 0: case 1: case 2: case 3: return null;
        case 4: case 5:   return makeItemStack( STICKY_PISTON_ID, random.nextInt(6) + 6);
        case 6: case 7:   return makeItemStack( WEB_ID, random.nextInt(8) + 16 );
        case 8: case 9:   return makeItemStack( COOKIE_ID, random.nextInt(12)+12);
        case 10: case 11: return makeItemStack( DIAMOND_AXE_ID );
        case 12:		  return makeItemStack( ICE_ID, random.nextInt(12)+12 );
        case 13:		  return makeItemStack( SLIME_BALL_ID, random.nextInt( 12 ) + 12 );
        case 14: case 15: return makeItemStack( LAVA_BUCKET_ID );
        case 16:		  return makeItemStack( CHAINMAIL_CHESTPLATE_ID );
        case 17: case 18: return makeItemStack( MOB_SPAWNER_ID, random.nextInt( 3 ) + 1 );
        case 19:		  return makeItemStack( GREEN_RECORD_ID );
        case 20:		  return makeItemStack( ICE_ID, random.nextInt(8) + 16 );
        case 21:		  return makeItemStack( GOLDEN_APPLE_ID, random.nextInt( 6 ) + 6 );
        case 22:		  return makeItemStack( TNT_ID, random.nextInt( 12 ) + 8 );
        default:		  return makeItemStack( DIAMOND_ID, random.nextInt( 4 ) + 1 );
        }
    }
	  
	  private ItemStack getNormalStack() {
	        int rand = random.nextInt( 25 );
	        switch( rand ) {
	        case 0: case 1:   return null;
	        case 2: case 3:   return makeItemStack( IRON_SWORD_ID );
	        case 4: case 5:   return makeItemStack( MILK_BUCKET_ID);
	        case 6:           return makeItemStack( WEB_ID, random.nextInt(8) + 8 );
	        case 7:           return makeItemStack( IRON_SPADE_ID );
	        case 8: case 9:   return makeItemStack( STRING_ID);
	        case 10: case 11: return makeItemStack( EGG_ID, random.nextInt( 8 ) + 8 );
	        case 12: 		  return makeItemStack( WATCH_ID);
	        case 13: case 14: case 15: return makeItemStack( IRON_PICKAXE_ID );
	        case 16: 		  return makeItemStack( BUCKET_ID);
	        case 17:		  return makeItemStack( MAP_ID);
	        case 18: case 19: return makeItemStack( BONE_ID, random.nextInt( 4 ) + 1 );
	        case 20: case 21: return makeItemStack( APPLE_ID, random.nextInt( 2 ) + 1 );
	        case 22:		  return makeItemStack( COMPASS_ID);
	        case 23:		  return makeItemStack( IRON_INGOT_ID, random.nextInt( 5 ) + 3 );
	        default:		  return getLootStack();
	        }
	    }
    */

	
	
    private int rotateMetadata( int blockID, int metadata) {
		int tempdata = 0;
		switch( blockID ) {
		case WOOD_STAIRS_ID: case COBBLESTONE_STAIRS_ID: case BRICK_STAIRS_ID: case STONE_BRICK_STAIRS_ID:
			return STAIRS_DIR_TO_META[rotateMetaDirToLocalAxes(STAIRS_META_TO_DIR[metadata])+2];
			
		case TORCH_ID: case LEVER_ID: case REDSTONE_TORCH_OFF_ID: case REDSTONE_TORCH_ON_ID: case STONE_BUTTON_ID:
			// check to see if this is a switch or a button and is flagged as thrown
			if( blockID == LEVER_ID || blockID == STONE_BUTTON_ID ) {
				if( metadata - 8 > 0 ) {
					tempdata += 8;
					metadata -= 8;
				}
				// now see if it's a floor switch
				if( blockID == LEVER_ID && ( metadata == 5 || metadata == 6 ) ) {
					// we'll leave this as-is
					return metadata + tempdata;
				}
			} else if(metadata == 5 ){
				// torches on the floor.
				return metadata;
			}
			return TORCH_DIR_TO_META[rotateMetaDirToLocalAxes(TORCH_META_TO_DIR[metadata])+2] + tempdata;


		case LADDER_ID: case DISPENSER_ID: case FURNACE_ID: case BURNING_FURNACE_ID: case WALL_SIGN_ID: case PISTON_ID: case PISTON_EXTENSION_ID:
			if(blockID==PISTON_ID || blockID==PISTON_EXTENSION_ID){
				if( metadata - 8 >= 0 ) {
					//pushed or not, sticky or not
					tempdata += 8;
					metadata -= 8;
				}
				if(metadata==0 || metadata==1) return metadata + tempdata;
			}
			return LADDER_DIR_TO_META[rotateMetaDirToLocalAxes(LADDER_META_TO_DIR[metadata])+2];
			
		case RAILS_ID:
			switch( bDir ) {
			case DIR_NORTH:
				// flat tracks
				if( metadata == 0 ) { return 0; }
				if( metadata == 1 ) { return 1; }
				// ascending tracks
				if( metadata == 2 ) { return 2; }
				if( metadata == 3 ) { return 3; }
				if( metadata == 4 ) { return bHand==1 ? 4:5; }
				if( metadata == 5 ) { return bHand==1 ? 5:4; }
				// curves
				if( metadata == 6 ) { return bHand==1 ? 6:9; }
				if( metadata == 7 ) { return bHand==1 ? 7:8; }
				if( metadata == 8 ) { return bHand==1 ? 8:7; }
				if( metadata == 9 ) { return bHand==1 ? 9:6; }
			case DIR_EAST:
				// flat tracks
				if( metadata == 0 ) { return 1; }
				if( metadata == 1 ) { return 0; }
				// ascending tracks
				if( metadata == 2 ) { return 5; }
				if( metadata == 3 ) { return 4; }
				if( metadata == 4 ) { return bHand==1 ? 2:3; }
				if( metadata == 5 ) { return bHand==1 ? 3:2; }
				// curves
				if( metadata == 6 ) { return bHand==1 ? 7:6; }
				if( metadata == 7 ) { return bHand==1 ? 8:9; }
				if( metadata == 8 ) { return bHand==1 ? 9:8; }
				if( metadata == 9 ) { return bHand==1 ? 6:7; }
			case DIR_SOUTH:
				// flat tracks
				if( metadata == 0 ) { return 0; }
				if( metadata == 1 ) { return 1; }
				// ascending tracks
				if( metadata == 2 ) { return 3; }
				if( metadata == 3 ) { return 2; }
				if( metadata == 4 ) { return bHand==1 ? 5:4; }
				if( metadata == 5 ) { return bHand==1 ? 4:5; }
				// curves
				if( metadata == 6 ) { return bHand==1 ? 8:7; }
				if( metadata == 7 ) { return bHand==1 ? 9:6; }
				if( metadata == 8 ) { return bHand==1 ? 6:9; }
				if( metadata == 9 ) { return bHand==1 ? 7:8; }
			case DIR_WEST:
				// flat tracks
				if( metadata == 0 ) { return 1; }
				if( metadata == 1 ) { return 0; }
				// ascending tracks
				if( metadata == 2 ) { return 4; }
				if( metadata == 3 ) { return 5; }
				if( metadata == 4 ) { return bHand==1 ? 3:2; }
				if( metadata == 5 ) { return bHand==1 ? 2:3; }
				// curves
				if( metadata == 6 ) { return bHand==1 ? 9:8; }
				if( metadata == 7 ) { return bHand==1 ? 6:7; }
				if( metadata == 8 ) { return bHand==1 ? 7:6; }
				if( metadata == 9 ) { return bHand==1 ? 8:9; }
			}
			break;
		case WOODEN_DOOR_ID: case IRON_DOOR_BLOCK_ID:
			if( metadata - 8 >= 0 ) {
				// the top half of the door
				tempdata += 8;
				metadata -= 8;
			}
			if( metadata - 4 >= 0 ) {
				// the door has swung counterclockwise around its hinge
				tempdata += bHand==1 ? 4:0;
				metadata -= 4;
			}
			else {
				tempdata += bHand==1 ? 0:4;
			}
			switch( bDir ) {
			case DIR_NORTH:
				if( metadata == 0 ) { return (bHand==1 ? 0:3) + tempdata; }
				if( metadata == 1 ) { return (bHand==1 ? 1:2) + tempdata; }
				if( metadata == 2 ) { return (bHand==1 ? 2:1) + tempdata; }
				if( metadata == 3 ) { return (bHand==1 ? 3:0) + tempdata; }
			case DIR_EAST:
				if( metadata == 0 ) { return (bHand==1 ? 1:0) + tempdata; }
				if( metadata == 1 ) { return (bHand==1 ? 2:3) + tempdata; }
				if( metadata == 2 ) { return (bHand==1 ? 3:2) + tempdata; }
				if( metadata == 3 ) { return (bHand==1 ? 0:1) + tempdata; }
			case DIR_SOUTH:
				if( metadata == 0 ) { return (bHand==1 ? 2:1) + tempdata; }
				if( metadata == 1 ) { return (bHand==1 ? 3:0) + tempdata; }
				if( metadata == 2 ) { return (bHand==1 ? 0:3) + tempdata; }
				if( metadata == 3 ) { return (bHand==1 ? 1:2) + tempdata; }
			case DIR_WEST:
				if( metadata == 0 ) { return (bHand==1 ? 3:2) + tempdata; }
				if( metadata == 1 ) { return (bHand==1 ? 0:1) + tempdata; }
				if( metadata == 2 ) { return (bHand==1 ? 1:0) + tempdata; }
				if( metadata == 3 ) { return (bHand==1 ? 2:3) + tempdata; }
			}
			break;
			
		case PUMPKIN_ID: case JACK_O_LANTERN_ID: case DIODE_BLOCK_OFF_ID: case DIODE_BLOCK_ON_ID:
			if( blockID == DIODE_BLOCK_OFF_ID || blockID == DIODE_BLOCK_ON_ID ) {
				// check for the delay tick for repeaters
				if( metadata - 4 >= 0 ) {
					if( metadata - 8 >= 0 ) {
						if( metadata - 12 >= 0 ) {
							// four tick delay
							tempdata += 12;
							metadata -= 12;
						} else {
							// three tick delay
							tempdata += 8;
							metadata -= 8;
						}
					} else {
						// two tick delay
						tempdata += 4;
						metadata -= 4;
					}
				}
			}
			return PUMPKIN_DIR_TO_META[rotateMetaDirToLocalAxes(PUMPKIN_META_TO_DIR[metadata])+2] + tempdata;
			
		case BED_BLOCK_ID: case FENCE_GATE_ID:
			if(blockID==BED_BLOCK_ID){
				if( metadata - 8 >= 0 ) {
					// this is the foot of the bed block.
					tempdata += 8;
					metadata -= 8;
				}
			}else if(blockID==FENCE_GATE_ID){
				if( metadata - 4 >= 0 ) {
					//open or closed
					tempdata += 4;
					metadata -= 4;
				}
			}
			return BED_DIR_TO_META[rotateMetaDirToLocalAxes(BED_META_TO_DIR[metadata])+2] + tempdata;
		
		case VINES_ID:
			if(metadata==0) return 0;
			return VINES_DIR_TO_META[rotateMetaDirToLocalAxes(VINES_META_TO_DIR[metadata])+2];
			
		case TRAP_DOOR_ID:
			if( metadata - 4 >= 0){
				tempdata += 4;
				metadata -= 4;
			}
			return TRAPDOOR_DIR_TO_META[rotateMetaDirToLocalAxes(TRAPDOOR_META_TO_DIR[metadata])+2] + tempdata;
			
		case SIGN_POST_ID:
			// sign posts
			switch( bDir ) {
			case DIR_NORTH:
				if( metadata == 0 ) { return bHand==1 ? 0:8; }
				if( metadata == 1 ) { return bHand==1 ? 1:7; }
				if( metadata == 2 ) { return bHand==1 ? 2:6; }
				if( metadata == 3 ) { return bHand==1 ? 3:5; }
				if( metadata == 4 ) { return 4; }
				if( metadata == 5 ) { return bHand==1 ? 5:3; }
				if( metadata == 6 ) { return bHand==1 ? 6:2; }
				if( metadata == 7 ) { return bHand==1 ? 7:1; }
				if( metadata == 8 ) { return bHand==1 ? 8:0; }
				
				if( metadata == 9 ) { return bHand==1 ? 9:15; }
				if( metadata == 10 ) { return bHand==1 ? 10:14; }
				if( metadata == 11 ) { return bHand==1 ? 11:13; }
				if( metadata == 12 ) { return 12; }
				if( metadata == 13 ) { return bHand==1 ? 13:11; }
				if( metadata == 14 ) { return bHand==1 ? 14:10; }
				if( metadata == 15 ) { return bHand==1 ? 15:9; }
			case DIR_EAST:
				if( metadata == 0 ) { return bHand==1 ? 4:12; }
				if( metadata == 1 ) { return bHand==1 ? 5:11; }
				if( metadata == 2 ) { return bHand==1 ? 6:10; }
				if( metadata == 3 ) { return bHand==1 ? 7:9; }
				if( metadata == 4 ) { return 8; }
				if( metadata == 5 ) { return bHand==1 ? 9:7; }
				if( metadata == 6 ) { return bHand==1 ? 10:6; }
				if( metadata == 7 ) { return bHand==1 ? 11:5; }
				if( metadata == 8 ) { return bHand==1 ? 12:4; }
				
				if( metadata == 9 ) { return bHand==1 ? 13:3; }
				if( metadata == 10 ) { return bHand==1 ? 14:2; }
				if( metadata == 11 ) { return bHand==1 ? 15:1; }
				if( metadata == 12 ) { return 0; }
				if( metadata == 13 ) { return bHand==1 ? 1:15; }
				if( metadata == 14 ) { return bHand==1 ? 2:14; }
				if( metadata == 15 ) { return bHand==1 ? 3:13; }
			case DIR_SOUTH:
				if( metadata == 0 ) { return bHand==1 ? 8:0; }
				if( metadata == 1 ) { return bHand==1 ? 9:15; }
				if( metadata == 2 ) { return bHand==1 ? 10:14; }
				if( metadata == 3 ) { return bHand==1 ? 11:13; }
				if( metadata == 4 ) { return 12; }
				if( metadata == 5 ) { return bHand==1 ? 13:11; }
				if( metadata == 6 ) { return bHand==1 ? 14:10; }
				if( metadata == 7 ) { return bHand==1 ? 15:9; }
				if( metadata == 8 ) { return bHand==1 ? 0:8; }
				
				if( metadata == 9 ) { return bHand==1 ? 1:7; }
				if( metadata == 10 ) { return bHand==1 ? 2:6; }
				if( metadata == 11 ) { return bHand==1 ? 3:5; }
				if( metadata == 12 ) { return 4; }
				if( metadata == 13 ) { return bHand==1 ? 5:3; }
				if( metadata == 14 ) { return bHand==1 ? 6:2; }
				if( metadata == 15 ) { return bHand==1 ? 7:1; }
			case DIR_WEST:
				if( metadata == 0 ) { return bHand==1 ? 12:4; }
				if( metadata == 1 ) { return bHand==1 ? 13:3; }
				if( metadata == 2 ) { return bHand==1 ? 14:2; }
				if( metadata == 3 ) { return bHand==1 ? 15:1; }
				if( metadata == 4 ) { return 0; }
				if( metadata == 5 ) { return bHand==1 ? 1:15; }
				if( metadata == 6 ) { return bHand==1 ? 2:14; }
				if( metadata == 7 ) { return bHand==1 ? 3:13; }
				if( metadata == 8 ) { return bHand==1 ? 4:12; }
				
				if( metadata == 9 ) { return bHand==1 ? 5:11; }
				if( metadata == 10 ) { return bHand==1 ? 6:10; }
				if( metadata == 11 ) { return bHand==1 ? 7:9; }
				if( metadata == 12 ) { return 8; }
				if( metadata == 13 ) { return bHand==1 ? 9:7; }
				if( metadata == 14 ) { return bHand==1 ? 10:6; }
				if( metadata == 15 ) { return bHand==1 ? 11:5; }
			}
		}
		// we should never get here, but users can be silly sometimes.
		return metadata + tempdata;
	}
    
    public static boolean metaValueIsValid(int blockID, int metadata){
    	if(metadata<0 || metadata >=16) return false;
    	
    	//note, cases where full 0-16 range is valid have been removed
    	switch( blockID ) {
    		//non-orientation metas, comment these out for now
    		/*
    		case LOG_ID:
    			return metadata < 3;
    		case SOIL_ID:
    			return metadata < 9;
    		case CROPS_ID:
    			return metadata < 8;
    		case PUMPKIN_STEM_ID: case MELON_STEM_ID:
    			return metadata < 8;
    		case CAKE_BLOCK_ID:
    			return metadata < 6;
    		*/
    		
    			
    		//orientation metas
			case RAILS_ID:
				return metadata < 10;
			case TORCH_ID: case REDSTONE_TORCH_OFF_ID: case REDSTONE_TORCH_ON_ID:
				return metadata > 0 &&  metadata <6;
			case LEVER_ID: case STONE_BUTTON_ID:
				if(metadata > 8) metadata-=8;
				return metadata!=0 && metadata < 7;
			case WOOD_STAIRS_ID: case COBBLESTONE_STAIRS_ID: case BRICK_STAIRS_ID: case STONE_BRICK_STAIRS_ID:
				return metadata < 4;
			case LADDER_ID: case DISPENSER_ID: case FURNACE_ID: case BURNING_FURNACE_ID: case WALL_SIGN_ID: case PAINTING_SPECIAL_ID:
			case PISTON_ID: case PISTON_EXTENSION_ID:
				if(metadata >= 8) metadata-=8;
				return metadata < 6;
			case PUMPKIN_ID: case JACK_O_LANTERN_ID:
				return metadata < 4;
			case FENCE_GATE_ID:
				return metadata < 8;
			case VINES_ID:
				return metadata==0 || metadata==1 || metadata==2 || metadata==4 || metadata==8;
			case TRAP_DOOR_ID:
				return metadata < 8;
    	}
    	
    	if(IS_HUMANS_PLUS_FLAG[blockID]) return metadata > 1 && metadata < 6;
    	
    	return true;
    }
    
    public final static int AIR_ID=0;
    public final static int STONE_ID=1;
    public final static int GRASS_ID=2;
    public final static int DIRT_ID=3;
    public final static int COBBLESTONE_ID=4;
    public final static int WOOD_ID=5;
    public final static int SAPLING_ID=6;
    public final static int BEDROCK_ID=7;
    public final static int WATER_ID=8;
    public final static int STATIONARY_WATER_ID=9;
    public final static int LAVA_ID=10;
    public final static int STATIONARY_LAVA_ID=11;
    public final static int SAND_ID=12;
    public final static int GRAVEL_ID=13;
    public final static int GOLD_ORE_ID=14;
    public final static int IRON_ORE_ID=15;
    public final static int COAL_ORE_ID=16;
    public final static int LOG_ID=17;
    public final static int LEAVES_ID=18;
    public final static int SPONGE_ID=19;
    public final static int GLASS_ID=20;
    public final static int LAPIS_ORE_ID=21;
    public final static int LAPIS_BLOCK_ID=22;
    public final static int DISPENSER_ID=23;
    public final static int SANDSTONE_ID=24;
    public final static int NOTE_BLOCK_ID=25;
    public final static int BED_BLOCK_ID=26;
    public final static int POWERED_RAIL_ID=27;
    public final static int DETECTOR_RAIL_ID=28;
    public final static int STICKY_PISTON_ID=29;
    public final static int WEB_ID=30;
    public final static int LONG_GRASS_ID=31;
    public final static int DEAD_BUSH_ID=32;
    public final static int PISTON_ID=33;
    public final static int PISTON_EXTENSION_ID=34;
    public final static int WOOL_ID=35;
    public final static int YELLOW_FLOWER_ID=37;
    public final static int RED_ROSE_ID=38;
    public final static int BROWN_MUSHROOM_ID=39;
    public final static int RED_MUSHROOM_ID=40;
    public final static int GOLD_BLOCK_ID=41;
    public final static int IRON_BLOCK_ID=42;
    public final static int DOUBLE_STEP_ID=43;
    public final static int STEP_ID=44;
    public final static int BRICK_ID=45;
    public final static int TNT_ID=46;
    public final static int BOOKSHELF_ID=47;
    public final static int MOSSY_COBBLESTONE_ID=48;
    public final static int OBSIDIAN_ID=49;
    public final static int TORCH_ID=50;
    public final static int FIRE_ID=51;
    public final static int MOB_SPAWNER_ID=52;
    public final static int WOOD_STAIRS_ID=53;
    public final static int CHEST_ID=54;
    public final static int REDSTONE_WIRE_ID=55;
    public final static int DIAMOND_ORE_ID=56;
    public final static int DIAMOND_BLOCK_ID=57;
    public final static int WORKBENCH_ID=58;
    public final static int CROPS_ID=59;
    public final static int SOIL_ID=60;
    public final static int FURNACE_ID=61;
    public final static int BURNING_FURNACE_ID=62;
    public final static int SIGN_POST_ID=63;
    public final static int WOODEN_DOOR_ID=64;
    public final static int LADDER_ID=65;
    public final static int RAILS_ID=66;
    public final static int COBBLESTONE_STAIRS_ID=67;
    public final static int WALL_SIGN_ID=68;
    public final static int LEVER_ID=69;
    public final static int STONE_PLATE_ID=70;
    public final static int IRON_DOOR_BLOCK_ID=71;
    public final static int WOOD_PLATE_ID=72;
    public final static int REDSTONE_ORE_ID=73;
    public final static int GLOWING_REDSTONE_ORE_ID=74;
    public final static int REDSTONE_TORCH_OFF_ID=75;
    public final static int REDSTONE_TORCH_ON_ID=76;
    public final static int STONE_BUTTON_ID=77;
    public final static int SNOW_ID=78;
    public final static int ICE_ID=79;
    public final static int SNOW_BLOCK_ID=80;
    public final static int CACTUS_ID=81;
    public final static int CLAY_ID=82;
    public final static int SUGAR_CANE_BLOCK_ID=83;
    public final static int JUKEBOX_ID=84;
    public final static int FENCE_ID=85;
    public final static int PUMPKIN_ID=86;
    public final static int NETHERRACK_ID=87;
    public final static int SOUL_SAND_ID=88;
    public final static int GLOWSTONE_ID=89;
    public final static int PORTAL_ID=90;
    public final static int JACK_O_LANTERN_ID=91;
    public final static int CAKE_BLOCK_ID=92;
    public final static int DIODE_BLOCK_OFF_ID=93;
    public final static int DIODE_BLOCK_ON_ID=94;
    public final static int LOCKED_CHEST_ID=95;
    public final static int TRAP_DOOR_ID=96;
    public final static int SILVERFISH_BLOCK_ID=97;
    public final static int STONE_BRICK_ID=98;
    public final static int HUGE_BROWN_MUSHROOM_ID=99;
    public final static int HUGE_RED_MUSHROOM_ID=100;
    public final static int IRON_BARS_ID=101;
    public final static int GLASS_PANE_ID=102;
    public final static int MELON_ID=103;
    public final static int PUMPKIN_STEM_ID=104;
    public final static int MELON_STEM_ID=105;
    public final static int VINES_ID=106;
    public final static int FENCE_GATE_ID=107;
    public final static int BRICK_STAIRS_ID=108;
    public final static int STONE_BRICK_STAIRS_ID=109;
    
    //Special Blocks
    public final static int SPECIAL_BLOCKID_START=300, SPECIAL_BLOCKID_END=325;
	public final static int PRESERVE_ID=300;
	public final static int ZOMBIE_SPAWNER_ID=301;
	public final static int SKELETON_SPAWNER_ID=302;
	public final static int SPIDER_SPAWNER_ID=303;
	public final static int CREEPER_SPAWNER_ID=304;
	public final static int UPRIGHT_SPAWNER_ID=305;
	public final static int EASY_SPAWNER_ID=306;
	public final static int MEDIUM_SPAWNER_ID=307;
	public final static int HARD_SPAWNER_ID=308;
	public final static int EASY_CHEST_ID=309;
	public final static int MEDIUM_CHEST_ID=310;
	public final static int HARD_CHEST_ID=311;
	public final static int TOWER_CHEST_ID=312;
	public final static int PIG_ZOMBIE_SPAWNER_ID=313;
	public final static int ENDERMAN_SPAWNER_ID=314;
	public final static int CAVE_SPIDER_SPAWNER_ID=315;
	public final static int GHAST_SPAWNER_ID=316;
	public final static int WALL_STAIR_ID=319;
	public final static int PAINTING_SPECIAL_ID=320;
	public final static int HUMANS_PLUS_ASSASIN_FLAG_ID=321;
	public final static int HUMANS_PLUS_ROGUE_FLAG_ID=322;
	public final static int HUMANS_PLUS_BANDIT_FLAG_ID=323;
	public final static int HUMANS_PLUS_PEACEFUL_FLAG_ID=324;
	public final static int HUMANS_PLUS_SHADOW_FLAG_ID=325;
	public final static int HUMANS_PLUS_MILITIA_FLAG_ID=326;
	
	
	//Spawner Blocks from other mods
	//public final static int CASTLE_DEFENDERS_ENEMY_KNIGHT=231;
	//public final static int CASTLE_DEFENDERS_ENEMY_ARCHER=232;

    public final static int IRON_SPADE_ID=256;
    public final static int IRON_PICKAXE_ID=257;
    public final static int IRON_AXE_ID=258;
    public final static int FLINT_AND_STEEL_ID=259;
    public final static int APPLE_ID=260;
    public final static int BOW_ID=261;
    public final static int ARROW_ID=262;
    public final static int COAL_ID=263;
    public final static int DIAMOND_ID=264;
    public final static int IRON_INGOT_ID=265;
    public final static int GOLD_INGOT_ID=266;
    public final static int IRON_SWORD_ID=267;
    public final static int WOOD_SWORD_ID=268;
    public final static int WOOD_SPADE_ID=269;
    public final static int WOOD_PICKAXE_ID=270;
    public final static int WOOD_AXE_ID=271;
    public final static int STONE_SWORD_ID=272;
    public final static int STONE_SPADE_ID=273;
    public final static int STONE_PICKAXE_ID=274;
    public final static int STONE_AXE_ID=275;
    public final static int DIAMOND_SWORD_ID=276;
    public final static int DIAMOND_SPADE_ID=277;
    public final static int DIAMOND_PICKAXE_ID=278;
    public final static int DIAMOND_AXE_ID=279;
    public final static int STICK_ID=280;
    public final static int BOWL_ID=281;
    public final static int MUSHROOM_SOUP_ID=282;
    public final static int GOLD_SWORD_ID=283;
    public final static int GOLD_SPADE_ID=284;
    public final static int GOLD_PICKAXE_ID=285;
    public final static int GOLD_AXE_ID=286;
    public final static int STRING_ID=287;
    public final static int FEATHER_ID=288;
    public final static int SULPHUR_ID=289;
    public final static int WOOD_HOE_ID=290;
    public final static int STONE_HOE_ID=291;
    public final static int IRON_HOE_ID=292;
    public final static int DIAMOND_HOE_ID=293;
    public final static int GOLD_HOE_ID=294;
    public final static int SEEDS_ID=295;
    public final static int WHEAT_ID=296;
    public final static int BREAD_ID=297;
    public final static int LEATHER_HELMET_ID=298;
    public final static int LEATHER_CHESTPLATE_ID=299;
    public final static int LEATHER_LEGGINGS_ID=300;
    public final static int LEATHER_BOOTS_ID=301;
    public final static int CHAINMAIL_HELMET_ID=302;
    public final static int CHAINMAIL_CHESTPLATE_ID=303;
    public final static int CHAINMAIL_LEGGINGS_ID=304;
    public final static int CHAINMAIL_BOOTS_ID=305;
    public final static int IRON_HELMET_ID=306;
    public final static int IRON_CHESTPLATE_ID=307;
    public final static int IRON_LEGGINGS_ID=308;
    public final static int IRON_BOOTS_ID=309;
    public final static int DIAMOND_HELMET_ID=310;
    public final static int DIAMOND_CHESTPLATE_ID=311;
    public final static int DIAMOND_LEGGINGS_ID=312;
    public final static int DIAMOND_BOOTS_ID=313;
    public final static int GOLD_HELMET_ID=314;
    public final static int GOLD_CHESTPLATE_ID=315;
    public final static int GOLD_LEGGINGS_ID=316;
    public final static int GOLD_BOOTS_ID=317;
    public final static int FLINT_ID=318;
    public final static int PORK_ID=319;
    public final static int GRILLED_PORK_ID=320;
    public final static int PAINTING_ID=321;
    public final static int GOLDEN_APPLE_ID=322;
    public final static int SIGN_ID=323;
    public final static int WOOD_DOOR_ID=324;
    public final static int BUCKET_ID=325;
    public final static int WATER_BUCKET_ID=326;
    public final static int LAVA_BUCKET_ID=327;
    public final static int MINECART_ID=328;
    public final static int SADDLE_ID=329;
    public final static int IRON_DOOR_ID=330;
    public final static int REDSTONE_ID=331;
    public final static int SNOW_BALL_ID=332;
    public final static int BOAT_ID=333;
    public final static int LEATHER_ID=334;
    public final static int MILK_BUCKET_ID=335;
    public final static int CLAY_BRICK_ID=336;
    public final static int CLAY_BALL_ID=337;
    public final static int SUGAR_CANE_ID=338;
    public final static int PAPER_ID=339;
    public final static int BOOK_ID=340;
    public final static int SLIME_BALL_ID=341;
    public final static int STORAGE_MINECART_ID=342;
    public final static int POWERED_MINECART_ID=343;
    public final static int EGG_ID=344;
    public final static int COMPASS_ID=345;
    public final static int FISHING_ROD_ID=346;
    public final static int WATCH_ID=347;
    public final static int GLOWSTONE_DUST_ID=348;
    public final static int RAW_FISH_ID=349;
    public final static int COOKED_FISH_ID=350;
    public final static int INK_SACK_ID=351;
    public final static int BONE_ID=352;
    public final static int SUGAR_ID=353;
    public final static int CAKE_ID=354;
    public final static int BED_ID=355;
    public final static int DIODE_ID=356;
    public final static int COOKIE_ID=357;
    public final static int MAP_ID=358;
    public final static int SHEARS_ID=359;
    public final static int MELON_SLICE_ID=360;
    public final static int PUMPKIN_SEEDS_ID=361;
    public final static int MELON_SEEDS_ID=362;
    public final static int RAW_BEEF_ID=363;
    public final static int STEAK_ID=364;
    public final static int RAW_CHICKEN_ID=365;
    public final static int COOKED_CHICKEN_ID=366;
    public final static int ROTTEN_FLESH_ID=367;
    public final static int ENDER_PEARL_ID=368;
    public final static int GOLD_RECORD_ID=2256;
    public final static int GREEN_RECORD_ID=2257;
    
    
	//maps block metadata to a dir
	private static int[] 	BED_META_TO_DIR=new int[]	{		DIR_WEST,DIR_NORTH,DIR_EAST,DIR_SOUTH},
							TORCH_META_TO_DIR=new int[]	{0,		DIR_SOUTH,DIR_NORTH,DIR_WEST,DIR_EAST},
							STAIRS_META_TO_DIR=new int[]{		DIR_SOUTH,DIR_NORTH,DIR_WEST,DIR_EAST},
							LADDER_META_TO_DIR=new int[]{0,0,	DIR_EAST,DIR_WEST,DIR_NORTH,DIR_SOUTH},
							PUMPKIN_META_TO_DIR=new int[]{		DIR_EAST,DIR_SOUTH,DIR_WEST,DIR_NORTH},
							TRAPDOOR_META_TO_DIR=new int[]{		DIR_WEST,DIR_EAST,DIR_SOUTH,DIR_NORTH},
							VINES_META_TO_DIR=new int[]{0,		DIR_WEST,DIR_NORTH,0,DIR_EAST,0,0,0,DIR_SOUTH};
	
	//inverse map should be {North_inv,East_inv,dummy,West_inv,South_inv}
	private static int[] 	BED_DIR_TO_META			=new int[]{1,2,0,0,3},
							TORCH_DIR_TO_META		=new int[]{2,4,0,3,1},
							STAIRS_DIR_TO_META		=new int[]{1,3,0,2,0},
							LADDER_DIR_TO_META		=new int[]{4,2,0,3,5},
							PUMPKIN_DIR_TO_META		=new int[]{3,0,0,2,1},
							TRAPDOOR_DIR_TO_META	=new int[]{3,1,0,0,2},
							VINES_DIR_TO_META		=new int[]{2,4,0,1,8},
							PAINTING_DIR_TO_FACEDIR =new int[]{3,2,0,0,1};
    
    public final static boolean[] IS_WALL_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_WALLABLE=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_DELAY_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_LOAD_TRASMITER_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_LIQUID_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_NONSOLID_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_ORE_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    //public final static boolean[] IS_SPAWNER_BLOCK=new boolean[SPECIAL_BLOCKID_END+1];
    public final static boolean[] IS_HUMANS_PLUS_FLAG=new boolean[SPECIAL_BLOCKID_END+1];
    
    
    static{
    	for(int blockID=0;blockID<IS_WALL_BLOCK.length;blockID++){
    		if( blockID==COBBLESTONE_ID || blockID==WOOD_ID || blockID==LAPIS_BLOCK_ID || blockID==SANDSTONE_ID || blockID==GOLD_BLOCK_ID ||
    			blockID==STEP_ID || blockID==DOUBLE_STEP_ID || blockID==MOSSY_COBBLESTONE_ID || blockID==TORCH_ID || blockID==WOOD_STAIRS_ID ||
    		    blockID==LADDER_ID || blockID==COBBLESTONE_STAIRS_ID || blockID==OBSIDIAN_ID || blockID==GLOWSTONE_ID || blockID==IRON_BARS_ID || 
    		    blockID==GLASS_PANE_ID || blockID==STONE_BRICK_ID || blockID==FENCE_GATE_ID || blockID==BRICK_STAIRS_ID || blockID==STONE_BRICK_STAIRS_ID){
    			IS_WALL_BLOCK[blockID]=true;
    		} else IS_WALL_BLOCK[blockID]=false;
    		
    		if(blockID==AIR_ID || blockID==SAPLING_ID || blockID==WATER_ID || blockID==STATIONARY_WATER_ID || blockID==LOG_ID || blockID==LEAVES_ID ||
    		   blockID==LONG_GRASS_ID || blockID==DEAD_BUSH_ID || blockID==YELLOW_FLOWER_ID || blockID==RED_ROSE_ID ||
    		   blockID==BROWN_MUSHROOM_ID || blockID==RED_MUSHROOM_ID || blockID==SNOW_ID || blockID==ICE_ID || blockID==CACTUS_ID ||
    		   blockID==SUGAR_CANE_BLOCK_ID || blockID==MELON_ID || blockID==PUMPKIN_STEM_ID || blockID==MELON_STEM_ID || blockID==VINES_ID) {
    			IS_WALLABLE[blockID]=true;
    		} else IS_WALLABLE[blockID]=false;
    		
    
    		if(blockID==TORCH_ID || blockID==LEVER_ID || blockID==SIGN_POST_ID || blockID==SIGN_ID || blockID==REDSTONE_TORCH_ON_ID || blockID==REDSTONE_TORCH_OFF_ID || blockID==STONE_BUTTON_ID || blockID==GLOWSTONE_ID){
    			IS_DELAY_BLOCK[blockID]=true;
    		} else IS_DELAY_BLOCK[blockID]=false;
    		
    		if(blockID==AIR_ID || blockID==TORCH_ID || blockID==LADDER_ID || blockID==PRESERVE_ID || blockID==SAND_ID){
    			IS_LOAD_TRASMITER_BLOCK[blockID]=false;
    		} else IS_LOAD_TRASMITER_BLOCK[blockID]=true;
    		
    		if(blockID==WATER_ID || blockID==STATIONARY_WATER_ID || blockID==ICE_ID || blockID==LAVA_ID || blockID==STATIONARY_LAVA_ID){
    			IS_LIQUID_BLOCK[blockID]=true;
    		} else IS_LIQUID_BLOCK[blockID]=false;
    		
    		if(IS_LIQUID_BLOCK[blockID] || blockID==GRAVEL_ID || blockID==SAND_ID) IS_NONSOLID_BLOCK[blockID]=true;
    		else IS_NONSOLID_BLOCK[blockID]=false;
    		
    		if(blockID==COAL_ORE_ID || blockID==IRON_ORE_ID || blockID==GOLD_ORE_ID || blockID==LAPIS_ORE_ID || blockID==REDSTONE_ORE_ID||  blockID==DIAMOND_ORE_ID )
    			IS_ORE_BLOCK[blockID]=true;
    		else IS_ORE_BLOCK[blockID]=false;
    		
    		if(blockID==HUMANS_PLUS_ASSASIN_FLAG_ID || blockID==HUMANS_PLUS_ROGUE_FLAG_ID || blockID==HUMANS_PLUS_BANDIT_FLAG_ID || 
    		   blockID==HUMANS_PLUS_PEACEFUL_FLAG_ID || blockID==HUMANS_PLUS_MILITIA_FLAG_ID || blockID==HUMANS_PLUS_SHADOW_FLAG_ID)
    			IS_HUMANS_PLUS_FLAG[blockID]=true;
    		else IS_HUMANS_PLUS_FLAG[blockID]=false;
    		
    		/*if( blockID==MOB_SPAWNER_ID || blockID==ZOMBIE_SPAWNER_ID || blockID==SKELETON_SPAWNER_ID || blockID==SPIDER_SPAWNER_ID || blockID==CREEPER_SPAWNER_ID || 
        			blockID==UPRIGHT_SPAWNER_ID || blockID==EASY_SPAWNER_ID || blockID==MEDIUM_SPAWNER_ID || blockID==HARD_SPAWNER_ID || blockID==PIG_ZOMBIE_SPAWNER_ID ||
        			blockID==ENDERMAN_SPAWNER_ID || blockID==CAVE_SPIDER_SPAWNER_ID || blockID==CASTLE_DEFENDERS_ENEMY_KNIGHT || blockID==CASTLE_DEFENDERS_ENEMY_ARCHER
        			|| IS_HUMANS_PLUS_FLAG[blockID])
        			IS_SPAWNER_BLOCK[blockID]=true;
        		else IS_SPAWNER_BLOCK[blockID]=false;
        		*/
    	}
    }
    
    public final static int[] STEP_TO_STAIRS={WOOD_STAIRS_ID,WOOD_STAIRS_ID,WOOD_STAIRS_ID,COBBLESTONE_STAIRS_ID,BRICK_STAIRS_ID,STONE_BRICK_STAIRS_ID,WOOD_STAIRS_ID };
    
    public final static int blockToStepMeta(int[] idAndMeta){
    	if(!IS_WALL_BLOCK[idAndMeta[0]]) return 3;
    	switch(idAndMeta[0]){
			case SANDSTONE_ID:					return 1; 
			case COBBLESTONE_ID:				return 3;
			case BRICK_ID:						return 4;
			case STONE_BRICK_ID:				return 5;
			case STEP_ID: case DOUBLE_STEP_ID: 	return idAndMeta[1];
			default: 							return 2;
    	}
    }
    
    public final static boolean isValidRuleBlock(int blockID,BuildingExplorationHandler beh){
    	if(blockID >=  SPECIAL_BLOCKID_START && blockID <= SPECIAL_BLOCKID_END) return (beh.humansPlusLoaded || !IS_HUMANS_PLUS_FLAG[blockID]);
    	return Block.blocksList[blockID]!=null || blockID==AIR_ID;
    }
    
	public final static void circleShape(int diam){
		float rad=(float)diam/2.0F;
		float[][] shape_density=new float[diam][diam];
		for(int x=0;x<diam;x++)
			for(int y=0;y<diam;y++)
				shape_density[y][x]=(((float)x+0.5F-rad)*((float)x+0.5F-rad) + ((float)y+0.5F-rad)*((float)y+0.5F-rad))/(rad*rad);
		
		int[] xheight=new int[diam];
		for(int y=0; y<diam; y++){
			int x=0;
			for(; shape_density[y][x]>1.0F; x++){}
			xheight[y]=x;
		}
		
		CIRCLE_SHAPE[diam]=new int[diam][diam];
		CIRCLE_CRENEL[diam]=new int[diam][diam];
		SPHERE_SHAPE[diam]=new int[(diam+1)/2];
		int nextHeight=0,crenel_adj=0;
		for(int x=0;x<diam;x++) for(int y=0;y<diam;y++) { CIRCLE_SHAPE[diam][y][x]=0; CIRCLE_CRENEL[diam][y][x]=0; }
		for(int y=0; y<diam; y++){
			if(y==0 || y==diam-1) nextHeight = diam/2+1;
			else nextHeight = xheight[y<diam/2 ? y-1:y+1] + (xheight[y]==xheight[y<diam/2 ? y-1:y+1] ? 1:0);
			if(y>0 && xheight[y]==xheight[y-1]) crenel_adj++;
			
			int x=0;
			for(;x<xheight[y];x++) { 
				CIRCLE_SHAPE[diam][y][x]=-1; 
				CIRCLE_SHAPE[diam][y][diam-x-1]=-1;
				CIRCLE_CRENEL[diam][y][x]=-1;
				CIRCLE_CRENEL[diam][y][diam-x-1]=-1;
			}
			for(; x < nextHeight ; x++) { 
				CIRCLE_SHAPE[diam][y][x]=1; 
				CIRCLE_SHAPE[diam][y][diam-x-1]=1;
				CIRCLE_CRENEL[diam][y][x]=(x+crenel_adj)%2; 
				CIRCLE_CRENEL[diam][y][diam-x-1]=(x+crenel_adj+diam+1)%2; 
			}
		}
		for(int y=diam/2;y<diam;y++) 
			SPHERE_SHAPE[diam][y-diam/2]=(2*(diam/2-xheight[y])+(diam%2==0 ? 0:1) );
	}
	
	
	

	public final static int MAX_SPHERE_DIAM=40;
	public final static int[][] SPHERE_SHAPE=new int[MAX_SPHERE_DIAM+1][];
 	public final static int[][][] CIRCLE_SHAPE=new int[MAX_SPHERE_DIAM+1][][];
 	public final static int[][][] CIRCLE_CRENEL=new int[MAX_SPHERE_DIAM+1][][];
 	static{
 		for(int diam=1; diam<=MAX_SPHERE_DIAM; diam++){
 			circleShape(diam);
 		}
 		//change diam 6 shape to look better
 		CIRCLE_SHAPE[6]=new int[][]{{-1,-1, 1, 1,-1,-1},
				 				 	{-1, 1, 0, 0, 1,-1},
				 				 	{ 1, 0, 0, 0, 0, 1},
				 				 	{ 1, 0, 0, 0, 0, 1},
				 				 	{-1, 1, 0, 0, 1,-1},
				 				 	{-1,-1, 1, 1,-1,-1}};
 		CIRCLE_CRENEL[6]=new int[][]{{-1,-1, 1, 0,-1,-1},
				 				 	{-1, 0, 0, 0, 1,-1},
				 				 	{ 1, 0, 0, 0, 0, 0},
				 				 	{ 0, 0, 0, 0, 0, 1},
				 				 	{-1, 1, 0, 0, 0,-1},
				 				 	{-1,-1, 0, 1,-1,-1}};
 	}
 	
 	
 	private final static int LIGHTING_INVERSE_DENSITY=10;
 	private final static boolean[] randLightingHash=new boolean[512];
 	static{
 		Random rand=new Random();
 		for(int m=0; m<randLightingHash.length; m++)
 			randLightingHash[m]=rand.nextInt(LIGHTING_INVERSE_DENSITY)==0;
 	}
 	
 	public static String[] CHEST_TYPE_LABELS=new String[]{"EASY_CHEST_ITEMS","MEDIUM_CHEST_ITEMS","HARD_CHEST_ITEMS","TOWER_CHEST_ITEMS"};
 	public static int[] DEFAULT_CHEST_TRIES=new int[]{4,6,6,6};
	//chest items[n] format is array of 6 arrays
	//0array - idx
	//1array - blockId
	//2array - block damage/meta
	//3array - block weight
	//4array - block min stacksize
	//5array block max stacksize
	public static int[][][] DEFAULT_CHEST_ITEMS=new int[][][]{
		{	{0,ARROW_ID,0,1,1,12},
			{1,IRON_SWORD_ID,0,2,1,1},
			{2,GOLD_LEGGINGS_ID,0,1,1,1},
			{3,IRON_SPADE_ID,0,1,1,1},
			{4,STRING_ID,0,1,1,1},
			{5,EGG_ID,0,2,4,8},
			{6,WHEAT_ID,0,3,3,6},
			{7,IRON_PICKAXE_ID,0,2,1,1},
			{8,STORAGE_MINECART_ID,0,1,1,1},
			{9,BONE_ID,0,2,2,5},
			{10,COAL_ID,0,2,6,9},
			{11,LEATHER_HELMET_ID,0,1,1,1},
			{12,LADDER_ID,0,1,8,12}},
		{	{0,IRON_SWORD_ID,0,2,1,1},
			{1,MILK_BUCKET_ID,0,2,1,1},
			{2,WEB_ID,0,1,8,16},
			{3,IRON_SPADE_ID,0,1,1,1},
			{4,POWERED_MINECART_ID,0,1,1,1},
			{5,DIAMOND_HOE_ID,0,1,0,1},
			{6,WATCH_ID,0,1,1,1},
			{7,IRON_PICKAXE_ID,0,3,1,1},
			{8,BUCKET_ID,0,1,1,1},
			{9,MAP_ID,0,1,1,1},
			{10,APPLE_ID,0,2,2,3},
			{11,COMPASS_ID,0,1,1,1},
			{12,IRON_INGOT_ID,0,1,5,8},
			{13,ENDER_PEARL_ID,0,1,1,3}},
		{	{0,STICKY_PISTON_ID,0,2,6,12},
			{1,WEB_ID,0,1,8,24},
			{2,COOKIE_ID,0,2,12,24},
			{3,DIAMOND_AXE_ID,0,1,1,1},
			{4,ICE_ID,0,1,12,24},
			{5,SLIME_BALL_ID,0,2,12,24},
			{6,LAVA_BUCKET_ID,0,2,1,1},
			{7,CHAINMAIL_CHESTPLATE_ID,0,1,1,1},
			{8,MOB_SPAWNER_ID,0,2,2,4},
			{9,GREEN_RECORD_ID,0,1,1,1},
			{10,GOLDEN_APPLE_ID,0,1,4,8},
			{11,TNT_ID,0,2,8,20},
			{12,DIAMOND_ID,0,2,1,4}},
		{	{0,ARROW_ID,0,1,1,12},
			{1,RAW_FISH_ID,0,2,1,1},
			{2,GOLD_HELMET_ID,0,1,1,1},
			{3,ARROW_ID,0,1,1,12},
			{4,IRON_INGOT_ID,0,1,2,3},
			{5,STONE_SWORD_ID,0,1,1,1},
			{6,IRON_AXE_ID,0,1,1,1},
			{7,ICE_ID,0,2,8,16},
			{8,SADDLE_ID,0,1,1,1},
			{9,WHEAT_ID,0,2,3,6},
			{10,SULPHUR_ID,0,1,2,4},
			{11,LEATHER_CHESTPLATE_ID,0,1,1,1},
			{12,PUMPKIN_ID,0,1,1,5}}

	};

}
        