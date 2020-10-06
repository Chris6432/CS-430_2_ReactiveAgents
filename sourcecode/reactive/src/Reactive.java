package rla;

import java.util.Random;

import java.util.Iterator;
import java.util.LinkedList;

import mdpSolver.MDPSolver;

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
	public logist.topology.Topology.City startState;
	public logist.topology.Topology.City endState;

	public ActionMDP(logist.topology.Topology.City start, logist.topology.Topology.City end){
		this.startState = start;
		this.endState = end;
	}

	@Override
	public String toString() {
		return "Action from : " + this.startState + " to : " + this.endState;
	}
}

public class Reactive implements ReactiveBehavior {

	private Random random;
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

	private Double transitionProbabilities(ActionMDP action,StateMDP endState){ // this definition is actually fucked up, but at least it compiles
		if(endState.city != action.endState){
			return 0.0;
		}
		else{
			return this.td.probability(endState.city,endState.task);
		}
	}

	private Double rewardFunction(StateMDP startState, ActionMDP action){
		if(startState.task == action.endState){
			return (double) this.td.weight(action.startState,action.endState);
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
				System.out.println("transition pr from "+ ac.startState +" to "+ac.endState+" is : "+transitionProbabilities(ac, st));
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
				sum = sum + this.pPickup * this.transitionProbabilities(ac,stp)*this.V.get(cnt);
				cnt +=1;
			}
			val += sum;
			if (val > max && ac.startState == st.city){
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
			System.out.println("NO TASK,  path to " + ac.endState);
			action = new Move(ac.startState.pathTo(ac.endState).get(0));
		}
		else if(ac.endState == availableTask.deliveryCity){
			System.out.println("ACCEPTED, path to " + ac.endState);
			action = new Pickup(availableTask);
		}
		else{
			System.out.println("REJECTED, path to " + ac.endState);
			System.out.println(ac.startState.pathTo(ac.endState));
			action = new Move(ac.startState.pathTo(ac.endState).get(0));
			
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
			if(statePrime.city == action.endState){
				sum = sum + transitionProbabilities(action, statePrime) * V.get(cnt);
			}
			cnt = cnt + 1;
		}

		return sum;
	}

	private LinkedList<Double> valIterate(){
		LinkedList<Double> V = new LinkedList<Double>();

		for(StateMDP st : this.states){
			V.add(0.0);
		}

		System.out.println("Solving MDP");
		for(int i = 0; i < 100; i++){ // loop until good enough, replace with a while and some end-condition
			int cnt = 0;
			for(StateMDP st : this.states){
				LinkedList<Double> Q = new LinkedList<Double>();
				for(ActionMDP ac: this.actions){
					Q.add(this.rewardFunction(st, ac)+this.pPickup* sumV(st, ac, V));
				}
				V.set(cnt,this.max(Q));
				cnt = cnt + 1;
			}

		}
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

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;

		this.generatePolicy();
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		

		StateMDP st = this.computeState(vehicle, availableTask);

		ActionMDP ac = this.policy(st);

		Action action = this.decideAction(ac, availableTask);
		
		//System.out.println("Setp number : " + numActions + ";");
		//System.out.println("State is : " + st + ";");
		//System.out.println("Action is : " + ac + ";");



		/*
		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		*/
		//if (numActions >= 1) {
			//System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		//}
		numActions++;
		
		return action;
	}
}
