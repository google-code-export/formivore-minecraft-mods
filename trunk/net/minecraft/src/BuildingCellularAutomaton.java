package net.minecraft.src;

import java.io.PrintWriter;
import java.util.Random;
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
 * BuildingCellularAutomaton creates double-ended walls
 */
public class BuildingCellularAutomaton extends Building {
	private byte[][][] layers = null;
	public byte[][] seed=null;
	private byte[][] caRule=null;
	private final static byte DEAD=0,ALIVE=1;
	public final static byte DIE=-1,NOCHANGE=0,LIVE=1;
	private int MaxOscillatorCullStep;
	public int minFilledX, maxFilledX, minFilledY,maxFilledY;
	
	public BuildingCellularAutomaton(WorldGeneratorThread wgt_,TemplateRule bRule_,int bDir_,int axXHand_, boolean centerAligned_,int width, int height, int length, int MaxOscillatorCullStep_, byte[][] seed_, byte[][] caRule_, int[] sourcePt){
		super(0,wgt_, bRule_, bDir_,axXHand_,centerAligned_,new int[]{width,height,length},sourcePt);
		seed=seed_;
		MaxOscillatorCullStep=MaxOscillatorCullStep_;
		if((bWidth - seed.length)%2 !=0 ) bWidth++; //so seed can be perfectly centered
		if((bLength - seed[0].length)%2 !=0 ) bLength++;
		caRule=caRule_;
	}
	
	//unlike other Buildings, this should be called after plan()
	public boolean queryCanBuild(int ybuffer) throws InterruptedException{
		if(!( queryExplorationHandler(0,0,bLength-1) && queryExplorationHandler(bWidth-1,0,0) && queryExplorationHandler(bWidth-1,0,bLength-1) )){
			return false;
		}
		
		int layoutCode= bWidth*bLength > 120 ? WorldGeneratorThread.LAYOUT_CODE_TOWER : WorldGeneratorThread.LAYOUT_CODE_TEMPLATE;
    	if(wgt.isLayoutGenerator()){
	    	if(wgt.layoutIsClear(getIJKPt(0,0,ybuffer),getIJKPt(bWidth-1,0,bLength-1),layoutCode)){
	    		wgt.setLayoutCode(getIJKPt(0,0,ybuffer),getIJKPt(bWidth-1,0,bLength-1),layoutCode);
	    	} else return false;
    	}else{
    		if(isObstructedFrame(0,ybuffer)) return false;
    	}
		return true;
	}
	
	public boolean plan(){
		if(!shiftBuidlingJDown(15))
			return false;
		
		//layers is z-flipped from usual orientation so z=0 is the top
		layers=new byte[bHeight][bWidth][bLength];
		for(int z=0; z<bHeight; z++) for(int x=0; x<bWidth; x++) for(int y=0; y<bLength; y++)
			layers[z][x][y]=DEAD;
		
		
		minFilledX=(bWidth-seed.length)/2;
		maxFilledX=(bWidth-seed.length)/2+seed.length-1;
		minFilledY=(bLength-seed[0].length)/2;
		maxFilledY=(bLength-seed[0].length)/2+seed[0].length-1;
		for(int x=0; x<seed.length; x++) for(int y=0; y<seed[0].length; y++)
			layers[0][minFilledX+x][minFilledY+y]=seed[x][y];
		
		
		int structureDeathHeight=-1;
		for(int z=1; z<bHeight; z++){
			boolean layerIsAlive=false;
			boolean layerIsFixed=true;
			boolean layerIsPeriod2=z>=2;
			boolean layerIsPeriod3=z>=3;
			for(int x=Math.max(0,minFilledX-1); x<=Math.min(bWidth-1,maxFilledX+1); x++){
				for(int y=Math.max(0,minFilledY-1); y<=Math.min(bLength-1,maxFilledY+1); y++){
					//try the 8 neighboring points in previous layer
					int neighbors=0;
					for(int x1=Math.max(x-1,0); x1<=Math.min(x+1,bWidth-1); x1++)
						for(int y1=Math.max(y-1,0); y1<=Math.min(y+1,bLength-1); y1++)
							if(!(x1==x && y1==y))
								neighbors+=layers[z-1][x1][y1];
					
					//update this layer based on the rule
					layers[z][x][y]=caRule[layers[z-1][x][y]][neighbors];
				
					//culling checks and update bounding box
					if(layers[z][x][y]==ALIVE){
						if(x<minFilledX) minFilledX=x;
						if(x>maxFilledX) maxFilledX=x;
						if(y<minFilledY) minFilledY=y;
						if(y>maxFilledY) maxFilledY=y;
						layerIsAlive=true;
					}
					if(layers[z][x][y]!=layers[z-1][x][y]) layerIsFixed=false;
					if(z>=2 && layers[z][x][y]!=layers[z-2][x][y]) layerIsPeriod2=false;
					if(z>=3 && layers[z][x][y]!=layers[z-3][x][y]) layerIsPeriod3=false;
				
			}}
			if(!layerIsAlive){
				if(z<MaxOscillatorCullStep-1) return false;
				structureDeathHeight=z+1;
				break;
			}
			if(layerIsFixed && z<MaxOscillatorCullStep) 
				return false;
			if(layerIsPeriod2 && z-1<MaxOscillatorCullStep) 
				return false;
			if(layerIsPeriod3 && z-2<MaxOscillatorCullStep) 
				return false;
			
		}
		
		//prune top layer
		int topLayerCount=0, secondLayerCount=0;
		for(int x=0; x<bWidth; x++){ for(int y=0; y<bLength; y++){
			topLayerCount+=layers[0][x][y];
			secondLayerCount+=layers[1][x][y];
		}}
		if(2*topLayerCount >= 3*secondLayerCount){
			for(int x=0; x<bWidth; x++){ for(int y=0; y<bLength; y++){
				if(layers[0][x][y]==ALIVE && layers[1][x][y]==DEAD)
					layers[0][x][y]=DEAD;
			}}
		}
		if(structureDeathHeight!=-1) bHeight=structureDeathHeight;
		
		//now resize building dimensions, we will only build from to filled part of layers[]
		bWidth=maxFilledX-minFilledX+1;
		bLength=maxFilledY-minFilledY+1;
		
		return true;
	}
		
	public void build(){
		for(int z=0; z<bHeight; z++){
			for(int x=0; x<bWidth; x++){ for(int y=0; y<bLength; y++){
				if(layers[z][x+minFilledX][y+minFilledY]==ALIVE)
					setBlockLocal(x,bHeight-z-1,y,bRule);
			}}
		}
		flushDelayed();
	}
	
	public boolean shiftBuidlingJDown(int maxShift){
		//try 4 corners and center
		int[] heights=new int[]{findSurfaceJ(world, getI(bWidth-1,0), getK(bWidth-1,0),world.field_35472_c,false,false),
							    findSurfaceJ(world, getI(0,bLength-1), getK(0,bLength-1),world.field_35472_c,false,false),
							    findSurfaceJ(world, getI(bWidth-1,bLength-1), getK(bWidth-1,bLength-1),world.field_35472_c,false,false),
							    findSurfaceJ(world, getI(bWidth/2,bLength/2), getK(bWidth/2,bLength/2),world.field_35472_c,false,false)
							    };
		int minHeight=j0;
		int maxHeight=j0;
		for(int height : heights){
			if(height < minHeight) minHeight=height;
			if(height > maxHeight) maxHeight=height;
		}
		if(maxHeight - minHeight > maxShift) return false;
		else j0=minHeight;
		return true;
	}

	
	public static byte[][]makeSymmetricSeed(int maxWidth, int maxLength, float seedDensity, Random random){
		int width=random.nextInt(maxWidth)+1,length=random.nextInt(maxLength)+1;
		byte[][] seed=new byte[width][length];
		
		for(int x=0; x<(width+1)/2; x++){ for(int y=0; y<(length+1)/2; y++){ 
			seed[x][y]=random.nextFloat() < seedDensity? ALIVE:DEAD;
			seed[width-x-1][y]=seed[x][y];
			seed[x][length-y-1]=seed[x][y];
			seed[width-x-1][length-y-1]=seed[x][y];
		}}
		return seed;
	}
	
	public static byte[][] makeLinearSeed(int maxWidth, Random random){
		if(maxWidth<=1) return new byte[][]{{ALIVE}}; //degenerate case
		
		int width=random.nextInt(random.nextInt(maxWidth-1)+1)+2; //random number in (2,maxWidth) inclusive, biased towards low end
		byte[][] seed=new byte[width][1];
		for(int x=0; x<width; x++)seed[x][0]=ALIVE;
		return seed;
	}
	
	public final static byte[][] parseCARule(String str, PrintWriter lw){
		try{
			byte[][] rule=new byte[][]{{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0}};
			
			String birthStr=str.split("/")[0].trim();
			String surviveStr=str.split("/")[1].trim();
			for(int n=1; n<birthStr.length(); n++){
				int digit=Integer.parseInt(birthStr.substring(n,n+1));
				rule[0][digit]=ALIVE;
			}
			for(int n=1; n<surviveStr.length(); n++){
				int digit=Integer.parseInt(surviveStr.substring(n,n+1));
				rule[1][digit]=ALIVE;
			}
			return rule;
		}catch(Exception e){
			if(lw!=null) lw.println("Error parsing automaton rule "+str+": "+e.getMessage());
			return null;
		}
	}
	
	public final static String ruleToString(byte[][] rule){
		StringBuilder sb=new StringBuilder(30);
		sb.append("B");
		for(int n=0; n<9; n++) if(rule[0][n]==ALIVE) sb.append(n);
		sb.append("S");
		for(int n=0; n<9; n++) if(rule[1][n]==ALIVE) sb.append(n);
		return sb.toString();
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
		{"B3/S23","Life"},
		{"B3/S23",""},
		{"B3/S23",""},
		{"B36/S125","2x2"},
		{"B36/S125",""},
		{"B36/S23","High Life"},
		{"B36/S23",""},
		{"B3/S012345678","Inkspots"},
		{"B3/S012345678",""},
		{"B45/S2345","45-rule"},
		{"B45/S2345",""},
		{"B2/S01","temple"},
		{"B35678/S015678","legged amoeba"},
		{"B35678/S015678",""},
		{"B35678/S015678",""}
	};

	/*
	public final static int[][] DEFAULT_BIN_CA_RULES= new int[][]{
		{130,138},
		{170,77},
		{110,74},
		{170,165},
		{170,170},
		{169,138},
		{170,134},
		{184,180}, 
		{164,160}
	};
	
	public static void intCodeToStr(int[] code){
		System.out.print("("+code[0]+","+code[1]+")=");
		boolean[][] arry=new boolean[2][9];
		arry[0][0]=false;
		arry[1][0]=false;
		for(int i=7; i>=0; i--){
			arry[0][8-i]=((code[0]>>i) & 0x1)>0;
			arry[1][8-i]=((code[1]>>i) & 0x1)>0;
		}
		System.out.print("L");
		for(int i=0; i<9; i++) System.out.print((arry[0][i]?1:0)+"");
		System.out.print("/D");
		for(int i=0; i<9; i++)System.out.print((arry[1][i]?1:0)+"");
		System.out.print("  ");
		
		String str="B";
		for(int i=0; i<9; i++){
			if(!arry[0][i] && !arry[1][i]){} //"not covered" unchanged
			if(arry[0][i] && !arry[1][i]){ str+=i; } //live
			if(!arry[0][i] && arry[1][i]){} //die
			if(arry[0][i] && arry[1][i]){} //die
		}
		str+="/S";
		for(int i=0; i<9; i++){
			if(!arry[0][i] && !arry[1][i]){ str+=i; } //unchanged
			if(arry[0][i] && !arry[1][i]){ str+=i; } //live
			if(!arry[0][i] && arry[1][i]){} //die
			if(arry[0][i] && arry[1][i]){} //die
		}
		System.out.println(str);
	}
	*/
	
}

