package net.minecraft.src;

public class BuildingDispenserTrap extends Building{
	public final static int ARROW_MISSILE=0, DAMAGE_POTION_MISSILE=1;
	
	public BuildingDispenserTrap(WorldGeneratorThread wgt_,TemplateRule bRule_,int bDir_,int plateSeparation,int[] sourcePt){
		super(0,wgt_, bRule_, bDir_,1,true,new int[]{3,6,plateSeparation},sourcePt);
	}
	
	public boolean queryCanBuild(int minLength){
		if(!isFloor(1,0,0)) return false;
		
		//search along floor for a height 2 wall. If we find it, reset bLength and return true.
		for(int y=1; y<9; y++){
			if(isFloor(1,0,y)) continue;
			if(!isWallBlock(1,-1,y)) return false;
			
			//now must have block at (1,0,y)...
			if(y<minLength) return false;
			if(!isWallable(1,1,y)){
				bLength=y;
				return true;
			}
			return false;
		}
		return false;
	}
	
	//      ---   bLength+3 - end of mechanism
	//      | |
	//      | |
	//      ---   y=bLength - mechanism start, 
	//       *    y==bLength-1 - end of redstone wire  
	//       *
	//       0    y=0 - trigger plate
	public void build(int missileType, boolean multipleTriggers){
		if(bLength<0) bLength=0;
		
		//System.out.println("Building dispenser trap at "+i0+","+j0+","+k0+", plateSeparation="+bLength);
		
		for(int x=0; x<MECHANISM[0][0].length; x++){
			for(int y=0; y<MECHANISM[0].length; y++){
				for(int z=0; z<MECHANISM.length; z++){
					if(MECHANISM[z][3-y][x]==1) 
						 setBlockLocal(x,z-3,y+bLength,bRule);
					else setBlockLocal(x,z-3,y+bLength,CODE_TO_BLOCK[MECHANISM[z][3-y][x]]);
		}}}
		
		for(int y=0; y<bLength;y++){
			setBlockLocal(1,-3,y,bRule);
			setBlockLocal(1,-2,y,REDSTONE_WIRE_ID);
			setBlockLocal(0,-2,y,bRule);
			setBlockLocal(2,-2,y,bRule);
			setBlockLocal(1,-1,y,bRule);
			setBlockLocal(1,0,y,multipleTriggers && random.nextBoolean() ? STONE_PLATE_ID : HOLE_ID);
			setBlockLocal(1,1,y,HOLE_ID);
		}
		setBlockLocal(1,0,0,STONE_PLATE_ID);
		
		flushDelayed();
		
		ItemStack itemstack= missileType==ARROW_MISSILE ? new ItemStack(ARROW_ID, 30+random.nextInt(10),0)
		                                                 :new ItemStack(POTION_ID,30+random.nextInt(10),12 | 0x4000);
		setItemDispenser(1,1,bLength+1,LADDER_DIR_TO_META[DIR_SOUTH],itemstack);
	}
	
	private void setItemDispenser(int x, int z, int y, int metaDir, ItemStack itemstack){
		int[] pt=getIJKPt(x,z,y);
		world.setBlock(pt[0], pt[1], pt[2], DISPENSER_ID);
		world.setBlockMetadataWithNotify(pt[0], pt[1], pt[2], LADDER_DIR_TO_META[orientDirToBDir(LADDER_META_TO_DIR[metaDir])]);
		try{	
		    TileEntityDispenser tileentitychest=(TileEntityDispenser)world.getBlockTileEntity(pt[0],pt[1],pt[2]);
		    if(itemstack != null && tileentitychest!=null)
		    	tileentitychest.setInventorySlotContents(random.nextInt(tileentitychest.getSizeInventory()), itemstack);
	    }catch(Exception e) { 
        	System.out.println("Error filling dispensert: "+e.toString());
        	e.printStackTrace();
        }
	}
	
	private static int[][] CODE_TO_BLOCK= new int[][]{
		{PRESERVE_ID,0},
		{},
		{AIR_ID,0},
		{REDSTONE_WIRE_ID,0},
		{REDSTONE_TORCH_ON_ID,BUTTON_DIR_TO_META[DIR_NORTH]},
		{REDSTONE_TORCH_OFF_ID,BUTTON_DIR_TO_META[DIR_SOUTH]},
		{REDSTONE_TORCH_ON_ID,BUTTON_DIR_TO_META[DIR_SOUTH]}
	};
	
	private static int[][][] MECHANISM= new int[][][]{
	   {{ 0, 0, 0},
		{ 0, 0, 0},
		{ 0, 1, 0},
		{ 0, 0, 0}},
			
	   {{ 0, 0, 0},
		{ 1, 1, 1},
		{ 1, 4, 1},
		{ 1, 1, 1}},
		
	   {{ 0, 0, 0},
		{ 1, 1, 1},
		{ 1, 1, 1},
		{ 1, 5, 1}},
		
	   {{ 0, 1, 0},
		{ 1, 1, 1},
		{ 1, 3, 1},
		{ 1, 1, 1}},
		
	   {{ 0, 1, 0},
		{ 1, 6, 1},
		{ 1, 0, 1},
		{ 1, 2, 1}},
		
	   {{ 0, 1, 0},
		{ 1, 1, 1},
		{ 1, 1, 1},
		{ 1, 1, 1}},
	};
}
