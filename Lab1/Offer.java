package ua.agentlab;

import jade.core.AID;

public class Offer {
	
	public AID agent;
	public int price;
	public int discount;
	
	Offer() {
		agent = null;
		price = 0;
		discount = 0;
	}
	
	Offer(AID aid) {
		agent = aid;
		price = 0;
		discount = 0;
	}

}
