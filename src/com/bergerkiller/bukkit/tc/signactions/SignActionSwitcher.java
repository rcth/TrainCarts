package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class SignActionSwitcher extends SignAction {

	private BlockMap<AtomicInteger> switchedTimes = new BlockMap<AtomicInteger>();
	private AtomicInteger getSwitchedTimes(Block signblock) {
		AtomicInteger i = switchedTimes.get(signblock);
		if (i == null) {
			i = new AtomicInteger();
			switchedTimes.put(signblock, i);
		}
		return i;
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("switcher", "tag")) return;
		boolean doCart = false;
		boolean doTrain = false;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_UPDATE) && info.isTrainSign()) {
			doTrain = true;
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_UPDATE) && info.isCartSign()) {
			doCart = true;
		} else if (info.isAction(SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
			info.setLevers(false);
			return;
		} else if (info.isAction(SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
			info.setLevers(false);
			return;
		} else {
			return;
		}
		if (!info.hasRailedMember()) {
			return;
		}
		if ((doCart || doTrain) && info.isFacing()) {
			//find out what statements to parse
			List<DirectionStatement> statements = new ArrayList<DirectionStatement>();
			statements.add(new DirectionStatement(info.getLine(2), Direction.LEFT));
			statements.add(new DirectionStatement(info.getLine(3), Direction.RIGHT));
			//other signs below this sign we could parse?
			for (Sign sign : info.findSignsBelow()) {
				boolean valid = true;
				for (String line : sign.getLines()) {
					DirectionStatement stat = new DirectionStatement(line);
					if (stat.direction == Direction.NONE) {
						valid = false;
						break;
					} else {
						statements.add(stat);
					}
				}
				if (!valid) {
					break;
				}
			}
			Block signblock = info.getBlock();
			while (MaterialUtil.ISSIGN.get(signblock = signblock.getRelative(BlockFace.DOWN))) {
				Sign sign = BlockUtil.getSign(signblock);
				if (sign == null) break;
				boolean valid = true;
				for (String line : sign.getLines()) {
					DirectionStatement stat = new DirectionStatement(line);
					if (stat.direction == Direction.NONE) {
						valid = false;
						break;
					} else {
						statements.add(stat);
					}
				}
				if (!valid) break;
			}
			//parse all of the statements
			//are we going to use a counter?
			int maxcount = 0;
			int currentcount = 0;
			AtomicInteger signcounter = null;
			for (DirectionStatement stat : statements) {
				if (stat.hasNumber()) {
					maxcount += stat.number;
					if (signcounter == null) {
						signcounter = getSwitchedTimes(info.getBlock());
						if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
							currentcount = signcounter.getAndIncrement();
						} else {
							currentcount = signcounter.get();
						}
					}
				}
			}
			if (signcounter != null && currentcount >= maxcount) {
				signcounter.set(1);
				currentcount = 0;
			}
			
			int counter = 0;
			Direction dir = Direction.NONE;
			for (DirectionStatement stat : statements) {
				if ((stat.hasNumber() && (counter += stat.number) > currentcount)
						|| (doCart && stat.has(info, info.getMember()))
						|| (doTrain && stat.has(info, info.getGroup()))) {

					dir = stat.direction;
					break;
				}
			}
			info.setLevers(dir != Direction.NONE);
			if (dir != Direction.NONE && info.isPowered()) {
				//handle this direction
				info.setRailsTo(dir);
				return; //don't do destination stuff
			}
		}

		//handle destination alternatively
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && info.hasMember()) {
			PathNode node = PathNode.getOrCreate(info);
			if (node != null) {
				PathConnection conn = null;
				if (doCart && info.hasMember()) {
					conn = node.findConnection(info.getMember().getProperties().getDestination());
				} else if (doTrain && info.hasGroup()) {
					conn = node.findConnection(info.getGroup().getProperties().getDestination());
				}
				if (conn != null) {
					info.setRailsTo(conn.direction);
					return;
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isType("switcher", "tag")) {
			if (event.isCartSign()) {
				return handleBuild(event, Permission.BUILD_SWITCHER, "cart switcher", "switch between tracks based on properties of the cart above");
			} else if (event.isTrainSign()) {
				return handleBuild(event, Permission.BUILD_SWITCHER, "train switcher", "switch between tracks based on properties of the train above");
			}
		}
		return false;
	}
}
