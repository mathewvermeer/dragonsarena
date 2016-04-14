package org.distsys.common.das.units;

import org.distsys.common.MessageSocket;
import org.distsys.common.das.BattleField;
import org.distsys.common.das.GameState;
import org.distsys.common.das.IMessageReceivedHandler;
import org.distsys.common.das.MessageRequest;
import org.distsys.common.messages.Message;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all players whom can
 * participate in the DAS game. All properties
 * of the units (hitpoints, attackpoints) are
 * initialized in this class.
 *
 * @author Pieter Anemaet, Boaz Pat-El
 */
public abstract class Unit implements Serializable, IMessageReceivedHandler {
	private static final long serialVersionUID = -4550572524008491160L;

	private static final Object lock = new Object();
	private static final String mmServer = "ec2-52-28-215-173.eu-central-1.compute.amazonaws.com";
	public static String masterServer;

	// Position of the unit
	protected int x, y;

	// Health
	private int maxHitPoints;
	protected int hitPoints;

	// Attack points
	protected int attackPoints;

	// Identifier of the unit
	private int unitID;

	// The communication socket between this client and the board
	protected transient MessageSocket slaveSocket;
	protected transient MessageSocket matchMakingSocket;
	protected transient MessageSocket masterSocket;

	// Map messages from their ids
	private Map<String, Message> messageList;
	// Is used for mapping an unique id to a message sent by this unit
	private int localMessageCounter = 0;

	// If this is set to false, the unit will return its run()-method and disconnect from the server
	protected boolean running;

	/* The thread that is used to make the unit run in a separate thread.
	 * We need to remember this thread to make sure that Java exits cleanly.
	 * (See stopRunnerThread())
	 */
	protected Thread runnerThread;

	public enum Direction {
		up, right, down, left
	}

	public enum UnitType {
		player, dragon, undefined,
	}

	/**
	 * Create a new unit and specify the
	 * number of hitpoints. Units hitpoints
	 * are initialized to the maxHitPoints.
	 *
	 * @param maxHealth is the maximum health of
	 *                  this specific unit.
	 */
	public Unit(int maxHealth, int attackPoints) {
		try {
			synchronized (lock) {
				matchMakingSocket = new MessageSocket(new Socket(mmServer, 44444));
				ObjectInputStream mmIn = matchMakingSocket.getObjectInputStream();
				Map<String, String> servers = (Map<String, String>) mmIn.readObject();
				matchMakingSocket.getSocket().close();
				if (servers == null) throw new RemoteException();
				masterServer = servers.get("master");
				masterSocket = new MessageSocket(new Socket(servers.get("master"), 33333));
				slaveSocket = new MessageSocket(new Socket(servers.get("slave"), 33333));

			}

			ObjectInputStream in = masterSocket.getObjectInputStream();
			Message message = (Message) in.readObject();
			String id = (String) message.get("id");
			unitID = Integer.parseInt(id.substring(1));

			PrintWriter writer = new PrintWriter(slaveSocket.getSocket().getOutputStream());
			writer.println("D" + unitID);
			writer.flush();

			new Thread(() -> {
				ObjectInputStream ois = null;
				ois = slaveSocket.getObjectInputStream();
				Message msg;
//				BattleField battleField;
				while (true) {
					try {
						msg = (Message) ois.readObject();
						processMessage(msg);
//						battleField = (BattleField) in.readObject();
//						BattleField.getBattleField().updateState(battleField);
					} catch (SocketTimeoutException e) {
					} catch (ClassNotFoundException | IOException e) {
						break;
					}
//					try {
//						Thread.sleep(50);
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
				}
				try {
					ois.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).start();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}

		messageList = new ConcurrentHashMap<>();

		// Initialize the max health and health
		hitPoints = maxHitPoints = maxHealth;

		// Initialize the attack points
		this.attackPoints = attackPoints;

		// Create a new socket
//        masterSocket = new SynchronizedSocket(localSocket);

	}

	/**
	 * Adjust the hitpoints to a certain level.
	 * Useful for healing or dying purposes.
	 *
	 * @param modifier is to be added to the
	 *                 hitpoint count.
	 */
	public synchronized void adjustHitPoints(int modifier) {
		if (hitPoints <= 0)
			return;

		hitPoints += modifier;

		if (hitPoints > maxHitPoints)
			hitPoints = maxHitPoints;

		if (hitPoints <= 0)
			removeUnit(x, y);
	}

	public void dealDamage(int x, int y, int damage) {
		/* Create a new message, notifying the board
		 * that a unit has been dealt damage.
		 */
		String id;
		Message damageMessage;
		synchronized (this) {
			id = getNewMessageId();

			damageMessage = new Message();
			damageMessage.put("request", MessageRequest.dealDamage);
			damageMessage.put("x", x);
			damageMessage.put("y", y);
			damageMessage.put("damage", damage);
			damageMessage.put("id", id);
		}

		try {
			sendMessage(damageMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void healDamage(int x, int y, int healed) {
		/* Create a new message, notifying the board
		 * that a unit has been healed.
		 */
		String id;
		Message healMessage;
		synchronized (this) {
			id = getNewMessageId();

			healMessage = new Message();
			healMessage.put("request", MessageRequest.healDamage);
			healMessage.put("x", x);
			healMessage.put("y", y);
			healMessage.put("healed", healed);
			healMessage.put("id", id);
		}

		try {
			sendMessage(healMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the maximum number of hitpoints.
	 */
	public int getMaxHitPoints() {
		return maxHitPoints;
	}

	/**
	 * @return the unique unit identifier.
	 */
	public int getUnitID() {
		return unitID;
	}

	/**
	 * Set the position of the unit.
	 *
	 * @param x is the new x coordinate
	 * @param y is the new y coordinate
	 */
	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * @return the x position
	 */
	public int getX() {
		return x;
	}

	/**
	 * @return the y position
	 */
	public int getY() {
		return y;
	}

	/**
	 * @return the current number of hitpoints.
	 */
	public int getHitPoints() {
		return hitPoints;
	}

	/**
	 * @return the attack points
	 */
	public int getAttackPoints() {
		return attackPoints;
	}

	/**
	 * Tries to make the unit spawn at a certain location on the battlefield
	 *
	 * @param x x-coordinate of the spawn location
	 * @param y y-coordinate of the spawn location
	 * @return true iff the unit could spawn at the location on the battlefield
	 */
	protected boolean spawn(int x, int y) {
		/* Create a new message, notifying the board
		 * the unit has actually spawned at the
		 * designated position. 
		 */
		String id = getNewMessageId();
		Message spawnMessage = new Message();
		spawnMessage.put("request", MessageRequest.spawnUnit);
		spawnMessage.put("x", x);
		spawnMessage.put("y", y);
		spawnMessage.put("unit", this);
		spawnMessage.put("id", id);

		// Send a spawn message
		try {
			sendMessage(spawnMessage);
//        } catch (IDNotAssignedException e) {
//            System.err.println("No server found while spawning unit at location (" + x + ", " + y + ")");
//            return false;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Wait for the unit to be placed
		getUnit(x, y);

		return true;
	}

	/**
	 * Returns whether the indicated square contains a player, a dragon or nothing.
	 *
	 * @param x: x coordinate
	 * @param y: y coordinate
	 * @return UnitType: the indicated square contains a player, a dragon or nothing.
	 */
	protected UnitType getType(int x, int y) {
		Message getMessage = new Message(), result;
		String id = getNewMessageId();
		getMessage.put("request", MessageRequest.getType);
		getMessage.put("x", x);
		getMessage.put("y", y);
		getMessage.put("id", id);
		getMessage.put("origin", unitID);

		// Send the getUnit message
		try {
			sendMessage(getMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}

		int i = 0;
		// Wait for the reply
		while (!messageList.containsKey(id)) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}

			if (i++ == 100) break;

			// Quit if the game window has closed
			if (!GameState.getRunningState())
				return UnitType.undefined;
		}

		if (i >= 100) {
			return null;
		}

		result = messageList.get(id);
		if (result == null) // Could happen if the game window had closed
			return UnitType.undefined;
		messageList.remove(id);

		if (result.get("type") == null) {
			System.out.println("asdf");
		}

		return (UnitType) result.get("type");
	}

	protected Unit getUnit(int x, int y) {
		Message getMessage = new Message(), result;
		String id = getNewMessageId();
		getMessage.put("request", MessageRequest.getUnit);
		getMessage.put("x", x);
		getMessage.put("y", y);
		getMessage.put("id", id);
		getMessage.put("origin", unitID);

		// Send the getUnit message
		try {
			sendMessage(getMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Wait for the reply
		while (!messageList.containsKey(id)) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}

			// Quit if the game window has closed
			if (!GameState.getRunningState())
				return null;
		}

		result = messageList.get(id);
		messageList.remove(id);

		return (Unit) result.get("unit");
	}

	protected void removeUnit(int x, int y) {
		Message removeMessage = new Message();
		String id = getNewMessageId();
		removeMessage.put("request", MessageRequest.removeUnit);
		removeMessage.put("x", x);
		removeMessage.put("y", y);
		removeMessage.put("id", id);

		// Send the removeUnit message
		try {
			sendMessage(removeMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
		BattleField.getBattleField().processMessage(removeMessage);
	}

	protected void moveUnit(int x, int y) {
		Message moveMessage = new Message();
		String id = getNewMessageId();
		moveMessage.put("request", MessageRequest.moveUnit);
		moveMessage.put("x", x);
		moveMessage.put("y", y);
		moveMessage.put("id", id);
		moveMessage.put("unit", this);
		moveMessage.put("origin", unitID);
		moveMessage.put("origx", this.getX());
		moveMessage.put("origy", this.getY());

		// Send the getUnit message
		try {
			sendMessage(moveMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}

		int i = 0;
		while (!messageList.containsKey(id)) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (i++ == 100) break;
		}

		messageList.remove(id);
	}

	private void sendMessage(Message message) throws IOException {
		if (masterSocket == null) return;
		ObjectOutputStream out = masterSocket.getObjectOutputStream();
		synchronized (out) {
			out.writeObject(message);
			out.flush();
		}
	}

	private String getNewMessageId() {
		return unitID + "-" + localMessageCounter++;
	}

	public void processMessage(Message message) {
		String id = (String) message.get("id");
		id = id.substring(0, id.indexOf('-'));
		int unitId = Integer.parseInt(id);
		if (unitId == this.unitID) {
			if (message.containsKey("unit"))
				message.put("unit", this);

			Message reply = BattleField.getBattleField().processMessage(message);
			if (reply != null)
				messageList.put((String) reply.get("id"), reply);
		}
	}

	// Disconnects the unit from the battlefield by exiting its run-state
	public void disconnect() {
		running = false;
	}

	/**
	 * Stop the running thread. This has to be called explicitly to make sure the program
	 * terminates cleanly.
	 */
	public void stopRunnerThread() {
		try {
			runnerThread.join();
		} catch (InterruptedException ex) {
			assert (false) : "Unit stopRunnerThread was interrupted";
		}

	}
}
