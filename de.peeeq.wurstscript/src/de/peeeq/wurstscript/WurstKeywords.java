package de.peeeq.wurstscript;

public class WurstKeywords {
	public static final String[] KEYWORDS = new String[] { "class", "return", "if", "else", "while", "for", "in", "break", "new", "null",
		"package", "endpackage", "function", "returns", "public", "private", "protected", "import", "initlater", "native", "nativetype", "extends",
		"interface", "implements", "module", "use", "abstract", "static", "thistype", "override", "immutable", "it", "array", "and",
		"or", "not", "this", "construct", "ondestroy", "destroy", "type", "constant", "endfunction", "nothing", "init", "castTo",
		"tuple", "div", "mod", "let", "from", "to", "downto", "step", "endpackage", "skip", "true", "false", "var", "instanceof",
		"super", "enum", "switch", "case", "default", "typeId", "begin", "end",
	// jurst keywords, maybe split the highlighters later...:	
	     "library","endlibrary","scope","endscope","requires","uses","needs","struct","endstruct",
	     "then","endif","loop","exithwhen","endloop","method","takes","endmethod","set","call","exitwhen",
	     "globals","endglobals","initializer","elseif"
	};
	
	public static final String[] JASSTYPES = new String[] { 
		"int", "integer", "real", "code", "boolean", "string",
		"agent",  "event",  "player",  "widget",  "unit",  "destructable", 
		 "item",  "ability",  "buff",  "force",  "group", 
		 "trigger",  "triggercondition",  "triggeraction",  "timer",  "location", 
		 "region",  "rect",  "boolexpr",  "sound",  "conditionfunc", 
		 "filterfunc",  "unitpool",  "itempool",  "race",  "alliancetype", 
		 "racepreference",  "gamestate",  "igamestate",  "fgamestate",  "playerstate", 
		 "playerscore",  "playergameresult",  "unitstate",  "aidifficulty",  "eventid", 
		 "gameevent",  "playerevent",  "playerunitevent",  "unitevent",  "limitop", 
		 "widgetevent",  "dialogevent",  "unittype",  "gamespeed",  "gamedifficulty", 
		 "gametype",  "mapflag",  "mapvisibility",  "mapsetting",  "mapdensity", 
		 "mapcontrol",  "playerslotstate",  "volumegroup",  "camerafield",  "camerasetup", 
		 "playercolor",  "placement",  "startlocprio",  "raritycontrol",  "blendmode", 
		 "texmapflags",  "effect",  "effecttype",  "weathereffect",  "terraindeformation", 
		 "fogstate",  "fogmodifier",  "dialog",  "button",  "quest", 
		 "questitem",  "defeatcondition",  "timerdialog",  "leaderboard",  "multiboard", 
		 "multiboarditem",  "trackable",  "gamecache",  "version",  "itemtype", 
		 "texttag",  "attacktype",  "damagetype",  "weapontype",  "soundtype", 
		 "lightning",  "pathingtype",  "image",  "ubersplat",  "hashtable" };
	
}
