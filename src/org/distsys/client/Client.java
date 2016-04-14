package org.distsys.client;

import org.distsys.common.das.BattleField;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import static org.distsys.common.das.Core.initializeGame;

public class Client {

	public static void main(String[] args) throws RemoteException, NotBoundException, MalformedURLException {
		initializeGame(true);

		/* Make sure both the battlefield and
		 * the socketmonitor close down.
		 */
		BattleField.getBattleField().shutdown();
		System.exit(0); // Stop all running processes
	}

}
