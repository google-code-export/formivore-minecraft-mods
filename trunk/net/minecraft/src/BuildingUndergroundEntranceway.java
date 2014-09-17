package net.minecraft.src;
/*
 *  Source code for the The Great Wall Mod and Walled City Generator Mods for the game Minecraft
 *  Copyright (C) 2014 by Noah Whitman (wakatakeru@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * BuildingUndergroundEntranceway builds a passageway from an underground city to the surface.
 */

public class BuildingUndergroundEntranceway extends Building{
	private final static int PASSAGE_HEIGHT=6, PASSAGE_WIDTH=4, SUPPORT_INTERVAL=6;
	private final static TemplateRule STONE_RULE=new TemplateRule(new int[]{STONE_ID,0});
	private TemplateWall ws;
	public BuildingWall street;
	private int stairsID;

	//****************************************  CONSTRUCTOR - BuildingUndergroundEntraceway  *************************************************************************************//
	public BuildingUndergroundEntranceway (int ID_,WorldGeneratorThread wgt_,TemplateWall ws_,int dir_,int[] sourcePt){
		super(ID_,wgt_, ws_.TowerRule,dir_,1,false,new int[]{PASSAGE_WIDTH,PASSAGE_HEIGHT,0},sourcePt);
		ws=ws_;
		stairsID=STEP_TO_STAIRS[blockToStepMeta(ws.rules[ws.template[0][0][ws.WWidth/2]].primaryBlock)];
		//wallBlockRule=new TemplateRule(new int[]{ws_.TowerBlock,0});
	}
	
	//****************************************  FUNCTION - build *************************************************************************************//
	public boolean build() throws InterruptedException{
		for(; bLength<WORLD_MAX_Y-j0; bLength++){
			if(!(queryExplorationHandlerForChunk(-1,0,0) && queryExplorationHandlerForChunk(PASSAGE_WIDTH,0,0))) return false;
			
			if(IS_WATER_BLOCK[getBlockIdLocal(0,bLength+PASSAGE_HEIGHT,bLength)]) return false;
			
			if(j0+bLength>Building.SEA_LEVEL-10 && isArtificialWallBlock(0,bLength+PASSAGE_HEIGHT,bLength)) return false;
			
			if(j0+bLength>Building.SEA_LEVEL && (IS_WALLABLE[getBlockIdLocal(0,bLength,bLength)] || IS_WALLABLE[getBlockIdLocal(PASSAGE_WIDTH-1,bLength,bLength)])){
				bLength--;
				break;
			}
		}
		
		//build a tower to mark top
		BuildingTower tower=new BuildingTower(0,this,true,4,bDir, bHand, false, 10, 7, 10,getIJKPt(-3,bLength,bLength-7));
		tower.build(0,0,true);
		
		//entranceway
		for(int z=0; z<bLength;z++){
			for(int x=-1; x<=PASSAGE_WIDTH; x++){
				for(int z1=1; z1<=PASSAGE_HEIGHT; z1++) {
					if(x==-1 || x==PASSAGE_WIDTH || z1==PASSAGE_HEIGHT){
						if(IS_FLOWING_BLOCK[getBlockIdLocal(x,z+z1,z)])
							setBlockLocal(x,z+z1,z,STONE_ID);
					}
					else setBlockLocal(x,z+z1,z,AIR_ID);
			}}
			for(int x=0; x<PASSAGE_WIDTH; x++){
				setBlockLocal(x, z, z, random.nextInt(100) < bRule.chance ? stairsID : 0,STAIRS_DIR_TO_META[DIR_NORTH]);
				buildDown(x, z-1, z, STONE_RULE,20,0,3);
			}
			flushDelayed();
			
			if(z%SUPPORT_INTERVAL==0 && z<=bLength-PASSAGE_HEIGHT){
				for(int z1=-1; z1<PASSAGE_HEIGHT; z1++) {
					setBlockLocal(0,z+z1,z,bRule);
					setBlockLocal(PASSAGE_WIDTH-1,z+z1,z,bRule);
				}
				for(int x=0; x<PASSAGE_WIDTH; x++)
					setBlockLocal(x,z+PASSAGE_HEIGHT-1,z,bRule);
			}
			if(z%SUPPORT_INTERVAL==SUPPORT_INTERVAL/2 && z<=bLength-PASSAGE_HEIGHT){
				if(random.nextInt(2)==0) setBlockLocal(0,z+PASSAGE_HEIGHT-3,z,EAST_FACE_TORCH_BLOCK);
				if(random.nextInt(2)==0) setBlockLocal(PASSAGE_WIDTH-1,z+PASSAGE_HEIGHT-3,z,WEST_FACE_TORCH_BLOCK);
			}
		}
		
		//reinforce start of entrance
		for(int y=bLength; y>=bLength-PASSAGE_HEIGHT-4; y--){
			buildDown(-1, y>=bLength-PASSAGE_HEIGHT-1 ? bLength-1 : y+PASSAGE_HEIGHT, y, bRule,20,7,3);
			buildDown(PASSAGE_WIDTH, y>=bLength-PASSAGE_HEIGHT-1 ? bLength-1 : y+PASSAGE_HEIGHT, y, bRule,20,7,3);
			if(y<bLength-PASSAGE_HEIGHT){
				for(int x=0; x<PASSAGE_WIDTH; x++)
					setBlockLocal(x,y+PASSAGE_HEIGHT,y,bRule);
			}
			
		}
		//for(int x=0; x<PASSAGE_WIDTH; x++) buildDown(x, bLength-1, bLength, bRule,20,4,3);
		
		flushDelayed();
		
		street=new BuildingWall(bID,wgt,ws,flipDir(bDir),-bHand, ws.MaxL,true,getIJKPt((PASSAGE_WIDTH-ws.WWidth)/2,0,-1));
		street.plan(1, 0,BuildingWall.DEFAULT_LOOKAHEAD,true);
		
		return true;
	}
}
