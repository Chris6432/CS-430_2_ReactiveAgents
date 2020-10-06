

import java.util.LinkedList;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

class StateMDP{
	public logist.topology.Topology.City city;	// the city the task is in
	public logist.topology.Topology.City task; 	/** the city the task ends in we simply say 
													that no task is perfectly equivalent to a 
													task pointing to the city it started in */

	public StateMDP(logist.topology.Topology.City city, logist.topology.Topology.City task){
		this.city = city;
		this.task = task;
	}

	@Override
	public String toString() {
		if(this.city == this.task) return "In city : <" + this.city + "> with no task";
		return "In city : <" + this.city + "> with task to city : <" + this.task + ">";
	}
}

class ActionMDP{
	public logist.topology.Topology.City startCity;
	public logist.topology.Topology.City endCity;

	public ActionMDP(logist.topology.Topology.City start, logist.topology.Topology.City end){
		this.startCity = start;
		this.endCity = end;
	}

	@Override
	public String toString() {
		return "Action from : " + this.startCity + " to : " + this.endCity;
	}
}

public class Reactive implements ReactiveBehavior {

	private double pPickup;
	private int numActions;
	private Agent myAgent;

	private logist.topology.Topology topology;
	private logist.task.TaskDistribution td;

	private LinkedList<StateMDP> states;
	private LinkedList<ActionMDP> actions;

	private LinkedList<Double> V;



	private LinkedList<StateMDP> generateStates(){
		LinkedList<StateMDP> statesGen = new LinkedList<StateMDP>();

		for (City start : topology) {
			for (City stop : topology) {
				statesGen.add(new StateMDP(start,stop));
			}
		}

		return statesGen;
	}

	private LinkedList<ActionMDP> generateActions(){
		LinkedList<ActionMDP> actionsGen = new LinkedList<ActionMDP>();

		for (City start : topology){
			for (City stop : topology) {
				if(start != stop){
					actionsGen.add(new ActionMDP(start,stop));
				}
			}
		}

		return actionsGen;
	}

	private Double transitionProbabilities(StateMDP startState, ActionMDP action,StateMDP endState){ // this definition is actually fucked up, but at least it compiles
		double probability;
		
		if (startState.city != action.startCity || endState.city != action.endCity) {
			probability = 0.0;
		}
		
		else {
			probability = td.probability(endState.city,endState.task);
		}
		
		return probability;
	}

	private Double rewardFunction(StateMDP startState, ActionMDP action){
		if(startState.task == action.endCity){
			return (double) this.td.reward(action.startCity,action.endCity);
		}
		else{
			return 0.0;
		}
	}

	private void generatePolicy(){

		this.states = this.generateStates();
		this.actions = this.generateActions();
		
		this.V = this.valIterate();

		//System.out.println(this.actions.size());

		/*for(StateMDP st : this.states){
			System.out.println(st + " possible reward is : " + td.reward(st.city,st.task));
		}*/

		/*for (StateMDP st : this.states) {
			for (ActionMDP ac: this.actions) {
				System.out.println("transition pr from "+ ac.startCity +" to "+ac.endCity+" is : "+transitionProbabilities(ac, st));
			}
		}*/

	}

	private ActionMDP policy(StateMDP st){
		Double max = 0.0;
		ActionMDP act = this.actions.get(0);

		for (ActionMDP ac : this.actions){
			Double val = this.rewardFunction(st, ac);
			Double sum = 0.0;
			int cnt = 0;
			for (StateMDP stp: this.states){
				sum += this.pPickup * this.transitionProbabilities(st,ac,stp)*this.V.get(cnt);
				cnt +=1;
			}
			val += sum;
			if (val > max && ac.startCity == st.city){
				max = val;
				act = ac;
			}
		}

		return act;
	}

	private StateMDP computeState(Vehicle vehicle,  Task availableTask){
		if (availableTask == null){
			return new StateMDP(vehicle.getCurrentCity(), vehicle.getCurrentCity());
		}
		else{
			return new StateMDP(vehicle.getCurrentCity(), availableTask.deliveryCity);
		}
	}

	private Action decideAction(ActionMDP ac,Task availableTask){
		Action action;
		if(availableTask == null){
			action = new Move(ac.startCity.pathTo(ac.endCity).get(0));
		}
		else if(ac.endCity == availableTask.deliveryCity){
			action = new Pickup(availableTask);
		}
		else{
			System.out.println(ac.startCity.pathTo(ac.endCity));
			action = new Move(ac.startCity.pathTo(ac.endCity).get(0));
			
		}

		return action;
		//action = new Move(currentCity.randomNeighbor(random));
		//action = new Pickup(availableTask);
	}

	private Double max(LinkedList<Double> list){
		Double max = -1.0;
		for(Double d: list){
			if(d > max){max = d;}
		}
		return max;
	}

	private Double sumV(StateMDP state, ActionMDP action, LinkedList<Double> V){
		Double sum = 0.0;
		int cnt = 0;
		for (StateMDP statePrime : this.states){
			if(statePrime.city == action.endCity){
				sum = sum + transitionProbabilities(state, action, statePrime) * V.get(cnt);
			}
			cnt = cnt + 1;
		}

		return sum;
	}

	private LinkedList<Double> valIterate(){
		LinkedList<Double> V = new LinkedList<Double>();
		LinkedList<Double> VPrev = new LinkedList<Double>();
		
		
		for(StateMDP st : this.states){
			V.add(0.0);
			VPrev.add(0.0);
		}
		
		double ndiff; 
		System.out.println("Solving MDP");
		do { // loop until good enough, replace with a while and some end-condition
			int count = 0;
			for(StateMDP st : this.states){
				LinkedList<Double> Q = new LinkedList<Double>();
				for(ActionMDP ac: this.actions){
					Q.add(this.rewardFunction(st, ac)+this.pPickup* sumV(st, ac, V));
				}
				V.set(count,this.max(Q));
				count++;
			}
			LinkedList<Double> diff = new LinkedList<Double>();
			
			for (int i = 0; i<this.states.size(); i++) {
				diff.add(i,V.get(i)-VPrev.get(i));
				VPrev.set(i, V.get(i));
			}
			ndiff = this.max(diff);
		} while(ndiff > 0.01*(1-this.pPickup)/(2*this.pPickup));
	
		System.out.println("Sim ready to run");
		return V;
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.topology = topology;
		this.td = td;
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;

		this.generatePolicy();
		
	}

	//@Override
	public Action act(Vehicle vehicle, Task availableTask) {
	

		StateMDP st = this.computeState(vehicle, availableTask);

		ActionMDP ac = this.policy(st);

		Action action = this.decideAction(ac, availableTask);
		
		System.out.println("My name is" + vehicle.name());
		
		System.out.println("Setp number : " + numActions + ";");
		System.out.println("State is : " + st + ";");
		System.out.println("Action is : " + ac + ";");



		/*
		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		*/
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}
