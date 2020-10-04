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
	public StateMDP startState;
	public StateMDP endState;

	public ActionMDP(StateMDP start, StateMDP end){
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

		for (StateMDP start : this.states){
			for (StateMDP stop : this.states) {
				if(start != stop){
					actionsGen.add(new ActionMDP(start,stop));
				}
			}
		}

		return actionsGen;
	}

	private Double transitionProbabilities(ActionMDP action,StateMDP state){
		if(state.city != action.endState.city){
			return 0.0;
		}
		else{
			return this.td.probability(action.endState.city,action.endState.task);
		}
	}

	private Double rewardFunction(StateMDP state, ActionMDP action){
		if(state.task == action.endState.city){
			return (double) this.td.weight(action.startState.city,action.endState.city);
		}
		else{
			return 0.0;
		}
	}

	private void generatePolicy(){

		this.states = this.generateStates();
		this.actions = this.generateActions();
		
		LinkedList<Double> V = this.valIterate();

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
			if(statePrime.city == action.endState.city){
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

		for(int i = 0; i < 100; i++){ // loop until good enough, replace with a while some end-condition
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
		Action action;

		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}
