package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Builds towers
 */


public class BuildingTML extends Building
{
	TemplateTML tmlt;

	//****************************************  CONSTRUCTOR - BuildingTML  *************************************************************************************//
	public BuildingTML (int ID_,WorldGeneratorThread wgt,int dir_,int axXHand_,TemplateTML tmlt_, int[] pt) {
		super(ID_,wgt, null, dir_,axXHand_,new int[]{tmlt_.width,tmlt_.height,tmlt_.length},pt);
		tmlt=tmlt_;
		j1-=tmlt.embed;
	}

	//****************************************  FUNCTION - build *************************************************************************************//
	public void build() {
		tmlt.setFixedRules(random);
		
		//build base
		int[][] base=tmlt.namedLayers.get("base");
		for(int y=0;y<bLength;y++){ for(int x=0;x<bWidth;x++){
			if(base!=null) 
				buildDown(x,-1,y,tmlt.rules[base[y][x]],tmlt.leveling,0,0);
			else fillDown(getSurfaceIJKPt(x,y,j1-1,false,true),j1-1,world);
		}}

		
		//clear overhead
		for(int z=bHeight; z<tmlt.cutIn+tmlt.embed; z++)
			for(int y=0;y<bLength;y++) for(int x=0;x<bWidth;x++)
				setBlockLocal(x,z,y,AIR_ID);


		//build
		for(int z=0;z<bHeight;z++)
			for(int y=0;y<bLength;y++) for(int x=0;x<bWidth;x++)
				setBlockAndMetadataLocal(x,z,y,tmlt.rules[tmlt.template[z][y][x]]);


		flushDelayed();
	}
	
	//****************************************  FUNCTION - isObstructedBody*************************************************************************************//
	/*
	private boolean isObstructedBody(TemplateTML tb){
    	for(int z1=Math.min(tb.height-1,2); z1<tb.height; z1++){
    		for(int x1=0; x1<tb.length; x1++)
    			if(tb.rules[tb.template[z1][tb.width-1][x1]].getBlock(random)[0]!=PRESERVE_ID && isWallBlock(x1,z1,tb.width-1)) return true;
    		for(int y1=1; y1<tb.width-1;y1++){
    			if(tb.rules[tb.template[z1][y1][0]].getBlock(random)[0]!=PRESERVE_ID && isWallBlock(0,z1,y1)) return true;
    			if(tb.rules[tb.template[z1][y1][tb.length-1]].getBlock(random)[0]!=PRESERVE_ID && isWallBlock(tb.length-1,z1,y1)) return true;
    		}
    			
    	}
    	return false;
    }
    */
	
	//****************************************  FUNCTION - queryCanBuild *************************************************************************************//
   public boolean queryCanBuild(int ybuffer) throws InterruptedException{
	   if(j1<=0) return false;
	   
    	if(!(queryExplorationHandler(0,0,bLength-1) && queryExplorationHandler(bWidth-1,0,bLength-1))){
			return false;
		}
    	
    	//Don't build if it would require leveling greater than tmlt.leveling
    	for(int y=0 ;y<bLength;y++) for(int x=0 ;x<bWidth;x++)
    		if(j1 - getSurfaceIJKPt(x,y,j1-1,false,true)[1] > tmlt.leveling + 1) return false;

    	
    	if(wgt.isLayoutGenerator()){
	    	if(wgt.layoutIsClear(this, tmlt.templateLayout,WorldGeneratorThread.LAYOUT_CODE_TEMPLATE)){
	    		wgt.setLayoutCode(this,tmlt.templateLayout,WorldGeneratorThread.LAYOUT_CODE_TEMPLATE);
	    	} else return false;
    	}else{
    		if(isObstructedFrame(0,ybuffer)) return false;
    	}
		return true;
    }


}