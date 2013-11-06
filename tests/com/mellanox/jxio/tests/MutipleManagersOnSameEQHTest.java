package com.mellanox.jxio.tests;

import com.mellanox.jxio.*;

import java.util.Random;

public class MutipleManagersOnSameEQHTest implements Runnable {

	public void run() {
		// /////////////////// Test 3 /////////////////////
		// Multiple session manager on the same EQH
		TestManager.print("*** Test 3: Multiple session client on the same EQH *** ");

		// Setup Multiple Clients Parameters
		String url;
		EventQueueHandler eqh;
		MySesPortalManager[] sManagerArray;
		int numOfSessionManagers = 3;

		TestManager.print("----- Setting up a event queue handler...");
		eqh = new EventQueueHandler();

		TestManager.print("----- Setting up a multiple (" + numOfSessionManagers + ") session managers...");
		Random portGenerator = new Random();
		sManagerArray = new MySesPortalManager[numOfSessionManagers];
		for (int i = 0; i < numOfSessionManagers; i++) {
			// Randomize Port
			int port = TestManager.portRangeLow
			        + portGenerator.nextInt(TestManager.portRangeHigh - TestManager.portRangeLow);

			// Get url
			url = "rdma://" + TestManager.hostname + ":" + port;

			TestManager.print("----- Setting up a session manager " + i + "(url=" + url + ")...");
			sManagerArray[i] = new MySesPortalManager(eqh, url);
		}

		TestManager.print("----- Run Event Loop......for " + numOfSessionManagers + " events or 1 sec");
		eqh.runEventLoop(numOfSessionManagers, 1000000 /* 1sec */);

		TestManager.print("------ Closing the session manager...");
		for (int i = 0; i < numOfSessionManagers; i++) {
			sManagerArray[i].close();
		}

		TestManager.print("----- Closing the event queue handler...");
		eqh.close();

		TestManager.setSuccess(3);
		TestManager.print("*** Test 3 Passed! *** ");
	}

	class MySesPortalManager {
		EventQueueHandler eqh;
		ServerPortal      man;

		MySesPortalManager(EventQueueHandler eqh, String url) {
			MyPortalManagerCallbacks c = new MyPortalManagerCallbacks();
			this.man = new ServerPortal(eqh, url, c);
		}

		public void close() {
			man.close();
		}

		class MyPortalManagerCallbacks implements ServerPortal.Callbacks {

			public void onSessionNew(long ptrSes, String uriSrc, String srcIP) {
				// dummy implementation
				TestManager.print("got on new event");
			}

			public void onSessionEvent(int session_event, String reason) {
				// dummy implementation
				TestManager.print("GOT EVENT " + session_event + "because of " + reason);
			}
		}
	}
}
