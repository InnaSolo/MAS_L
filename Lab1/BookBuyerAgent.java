package ua.agentlab;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BookBuyerAgent extends Agent {
	// The title of the book to buy
	private String targetBookTitle;
	// The list of known seller agents
	private AID[] sellerAgents;

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Buyer: " + getAID().getName() + ". Ready");

		// Get the title of the book to buy as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetBookTitle = (String) args[0];
			System.out.println("Buyer: " + getAID().getName() + ". Target book is " + targetBookTitle);

			// Add a TickerBehaviour that schedules a request to seller agents every minute
			addBehaviour(new TickerBehaviour(this, 30000) {
				protected void onTick() {
					System.out.println("Buyer: " + getAID().getName() + ". Trying to buy " + targetBookTitle);
					// Update the list of seller agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("book-selling");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println("\nFound the following seller agents:");
						sellerAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							sellerAgents[i] = result[i].getName();
							System.out.println(" - " + sellerAgents[i].getName());
						}
						System.out.println("\n");
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// Perform the request
					myAgent.addBehaviour(new RequestPerformer());
				}
			} );
		}
		else {
			// Make the agent terminate
			System.out.println("Buyer: " + getAID().getName() + ". No target book title specified");
			doDelete();
		}
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Buyer: " + getAID().getName() + " terminating");
	}

	/**
	   Inner class RequestPerformer.
	   This is the behaviour used by Book-buyer agents to request seller 
	   agents the target book.
	 */
	private class RequestPerformer extends Behaviour {
		private AID bestSeller; 	// The agent who provides the best offer 
		private int bestPrice;  	// The best offered price
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;
		
		private Offer[] offers = new Offer[sellerAgents.length];

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
					
					offers[i] = new Offer(sellerAgents[i]);
				} 
				cfp.setContent(targetBookTitle);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 						
						for (int i = 0; i < sellerAgents.length; ++i) {							
							if(offers[i].agent.equals(reply.getSender()))
								offers[i].price = Integer.parseInt(reply.getContent());
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// We received all replies
						step = 2; 
						repliesCnt = 0;
					}
				}
				else {
					block();
				}
				break;
				
			case 2:
				// Send the discount request to all sellers that provided offer
				ACLMessage discountRequest = new ACLMessage(ACLMessage.PROPOSE);
				discountRequest.addReceiver(bestSeller);
				discountRequest.setContent(targetBookTitle);
				discountRequest.setConversationId("book-trade");
				discountRequest.setReplyWith("discount" + System.currentTimeMillis());				
				for (int i = 0; i < sellerAgents.length; ++i) {
					discountRequest.addReceiver(sellerAgents[i]);
				} 				
				myAgent.send(discountRequest);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(discountRequest.getReplyWith()));
				step = 3;
				break;	
				
			case 3:      
				// Receive the discount request answers
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Discount request answer received
					if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {	
						// Seller will give the discount
						for (int i = 0; i < sellerAgents.length; ++i) {							
							if(offers[i].agent.equals(reply.getSender())) {
								offers[i].discount = Integer.parseInt(reply.getContent());
								System.out.println("Buyer: " + getAID().getName() + ". " + targetBookTitle + " could be purchased with discount " + offers[i].discount + "%" +
									" from agent " + reply.getSender().getName());
							}
						}
					}
					else {
						// Seller will not give the discount
						System.out.println("Buyer: " + getAID().getName() + ". There will be no discount from " + reply.getSender().getName());
					}					
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// We received all discount request answers
						// Looking for the best offer
						for (int i = 0; i < sellerAgents.length; ++i) {
							if (offers[i].price == 0) {
								continue;
							}
							if (bestSeller == null || (int) (offers[i].price - offers[i].price * offers[i].discount / 100) < bestPrice) {
								// This is the best offer at present
								bestPrice = (int) (offers[i].price - offers[i].price * offers[i].discount / 100);
								bestSeller = offers[i].agent;
							}
						}
						
						step = 4; 
					}
				}
				else {
					block();
				}
				break;						
			case 4:				
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetBookTitle);
				order.setConversationId("book-trade");
				order.setReplyWith("order" + System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 5;
				break;
			case 5:      
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println("Buyer: " + getAID().getName() + ". " + targetBookTitle + " successfully purchased from agent " + reply.getSender().getName());
						System.out.println("Price = " + bestPrice);
						myAgent.doDelete();
					}
					else {
						System.out.println("Attempt failed: requested book already sold.");
					}
					step = 6;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 4 && bestSeller == null) {
				System.out.println("Buyer: " + getAID().getName() + ". Attempt failed: " + targetBookTitle + " not available for sale");
			}
			return ((step == 4 && bestSeller == null) || step == 6);
		}
	}  // End of inner class RequestPerformer
}
