package ua.agentlab;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class BookSellerAgent extends Agent {
	// The catalogue of books for sale (maps the title of a book to its price)
	private Hashtable catalogue;
	// The GUI by means of which the user can add books in the catalogue
	private BookSellerGui myGui;

	// Put agent initializations here
	protected void setup() {
		// Create the catalogue
		catalogue = new Hashtable();

		// Create and show the GUI 
		myGui = new BookSellerGui(this);
		myGui.showGui();

		// Register the book-selling service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour serving queries from buyer agents
		addBehaviour(new OfferRequestsServer());
		
		// Add the behaviour serving discount queries from buyer agents
		addBehaviour(new DiscountRequestsServer());

		// Add the behaviour serving purchase orders from buyer agents
		addBehaviour(new PurchaseOrdersServer());
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Seller: " + getAID().getName() + " terminating");
	}

	/**
     This is invoked by the GUI when the user adds a new book for sale
	 */
	public void updateCatalogue(final String title, final int price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				catalogue.put(title, new Integer(price));
				System.out.println("Seller: " + getAID().getName() + ". " + title + " inserted into catalogue. Price = " + price);
			}
		} );
	}

	/**
	   Inner class OfferRequestsServer.
	   This is the behaviour used by Book-seller agents to serve incoming requests 
	   for offer from buyer agents.
	   If the requested book is in the local catalogue the seller agent replies 
	   with a PROPOSE message specifying the price. Otherwise a REFUSE message is
	   sent back.
	 */
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) catalogue.get(title);
				if (price != null) {
					// The requested book is available for sale. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price.intValue()));
				}
				else {
					// The requested book is NOT available for sale.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer
	
	
	/**
	   Inner class DiscountRequestsServer.
	   This is the behaviour used by Book-seller agents to serve incoming requests 
	   for discounts.
	   If discount is available seller should set proper value for it. Otherwise
	   buyer has to be told that discount is unavailable.
	 */
	
	private class DiscountRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String title = msg.getContent();
				// CFP Message received. Process it
				ACLMessage reply = msg.createReply();
				
				Random rand = new Random();
				
				boolean discountAvailable = rand.nextBoolean();
				if (catalogue.get(title) != null) {
					if (discountAvailable) {
						// The discount is available. Reply with the discount value
						Integer discount = (Integer) rand.nextInt(20);
						System.out.println("Seller: " + getAID().getName() + ". " + title + " could be sold to agent " + msg.getSender().getName() + " with discount " + discount + "%");
						reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
						reply.setContent(String.valueOf(discount.intValue()));
					}
					else {
						// The discount is NOT available.
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("discount is not-available");
						System.out.println("Seller: " + getAID().getName() + ". " + title + " can't be sold to agent " + msg.getSender().getName() + " with discount");
					}
					myAgent.send(reply);
				}
				else {
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("Book is not-available");
					System.out.println("Seller: " + getAID().getName() + ". There is no book with name " + title);
					myAgent.send(reply);
				}
			}
			else {
				block();
			}
		}
	}  // End of inner class DiscountRequestsServer

	/**
	   Inner class PurchaseOrdersServer.
	   This is the behaviour used by Book-seller agents to serve incoming 
	   offer acceptances (i.e. purchase orders) from buyer agents.
	   The seller agent removes the purchased book from its catalogue 
	   and replies with an INFORM message to notify the buyer that the
	   purchase has been sucesfully completed.
	 */
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) catalogue.remove(title);
				if (price != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println("Seller: " + getAID().getName() + ". " + title + " sold to agent " + msg.getSender().getName());
				}
				else {
					// The requested book has been sold to another buyer in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer
}
