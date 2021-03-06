package model.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import gis.GISPolygon;
import model.disease.Compartment;
import model.learning.SelectionStrategy;
import model.util.Heuristics;
import model.util.Randomizer;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.util.collections.Pair;
import simulation.EventScheduler;
import simulation.ParametersAdapter;
import simulation.SimulationBuilder;
import util.TickConverter;

public class Student extends CommunityMember {

	/**
	 * Minimum time difference between activities to have fun (unit: hours)
	 */
	public static final double MIN_TIME_TO_FUN = 0.5;

	/**
	 * Change frequency between fun places (unit: hours)
	 */
	public static final double FUN_CHANGE_FREQUENCY = 3;

	/**
	 * Student id
	 */
	private String id;

	/**
	 * Academic schedule
	 */
	private Schedule schedule;

	/**
	 * Scheduled departures
	 */
	protected Map<Integer, Double> scheduledDepartures;

	/**
	 * Create a new student agent
	 * 
	 * @param contextBuilder Reference to the simulation builder
	 * @param comparment     Compartment
	 * @param id             Student id
	 * @param outbreakTick   Outbreak tick
	 */
	public Student(SimulationBuilder contextBuilder, Compartment compartment,
			String id, double outbreakTick) {
		super(contextBuilder, compartment, outbreakTick);
		this.id = id;
		this.scheduledDepartures = new HashMap<>();
	}

	/**
	 * Assign an academic schedule
	 * 
	 * @param schedule Academic schedule
	 */
	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
	}

	/**
	 * Attend an academic activity at a teaching facility
	 * 
	 * @param teachingFacilityId Id of the teaching facility
	 */
	public void attendActivity(String teachingFacilityId) {
		Map<String, GISPolygon> teachingFacilities = this.simulationBuilder.teachingFacilities;
		GISPolygon teachingFacility = teachingFacilities
				.get(teachingFacilityId);
		moveToPolygon(teachingFacility, "");
	}

	/**
	 * Leave an academic activity. The student determines what to do next. If
	 * he/she has an activity in less than MIN_TIME_TO_FUN ticks he/she prefers
	 * to go there. In the other case, the student goes to have fun.
	 */
	public void leaveActivity() {
		double ticks = RepastEssentials.GetTickCount();
		Pair<Integer, Double> dayTime = TickConverter.ticksToDayTime(ticks);
		int day = dayTime.getFirst();
		double hour = dayTime.getSecond();
		AcademicActivity nextActivity = this.schedule
				.getNextAcademicActivity(day, hour);
		if (nextActivity != null) {
			double delta = nextActivity.getStartTime() - hour;
			if (delta < MIN_TIME_TO_FUN) {
				attendActivity(nextActivity.getTeachingFacilityId());
			} else {
				haveFun();
			}
		}
	}

	/**
	 * Go have fun at a shared area
	 */
	public void haveFun() {
		// Walk to shared area
		Map<String, GISPolygon> places = this.simulationBuilder.sharedAreas;
		places.putAll(this.simulationBuilder.eatingPlaces);
		SelectionStrategy selectionStrategy = ParametersAdapter
				.getSelectionStrategy();
		GISPolygon polygon = getRandomPolygon(places, selectionStrategy);
		moveToPolygon(polygon, "");
		// Schedule having fun in another place
		EventScheduler eventScheduler = EventScheduler.getInstance();
		double ticks = RepastEssentials.GetTickCount();
		Pair<Integer, Double> dayTime = TickConverter.ticksToDayTime(ticks);
		int day = dayTime.getFirst();
		double hour = dayTime.getSecond();
		double timeToNextEvent = -1;
		AcademicActivity nextActivity = this.schedule
				.getNextAcademicActivity(day, hour);
		if (nextActivity != null) {
			timeToNextEvent = nextActivity.getStartTime();
		} else if (this.scheduledDepartures.containsKey(day)) {
			timeToNextEvent = this.scheduledDepartures.get(day);
		}
		if (timeToNextEvent > ticks) {
			double delta = timeToNextEvent - hour;
			if (delta > FUN_CHANGE_FREQUENCY) {
				eventScheduler.scheduleOneTimeEvent(FUN_CHANGE_FREQUENCY, this,
						"haveFun");
			}
		}
	}

	/**
	 * Get student id
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Schedule activities
	 */
	@Override
	protected void scheduleActivities() {
		EventScheduler eventScheduler = EventScheduler.getInstance();
		List<ISchedulableAction> actions = new ArrayList<>();
		for (Group group : this.schedule.getGroups()) {
			for (AcademicActivity activity : group.getAcademicActivities()) {
				int day = activity.getDay();
				double arrivalShift = Randomizer.getRandomArrivalShift();
				double startTime = activity.getStartTime() - arrivalShift;
				String teachingFacilityId = activity.getTeachingFacilityId();
				double ticksToEvent = TickConverter.dayTimeToTicks(day,
						startTime);
				ISchedulableAction attendActivityAction = eventScheduler
						.scheduleRecurringEvent(ticksToEvent, this,
								TickConverter.TICKS_PER_WEEK, "attendActivity",
								teachingFacilityId);
				actions.add(attendActivityAction);
				double endTime = activity.getEndTime();
				ticksToEvent = TickConverter.dayTimeToTicks(day, endTime);
				ISchedulableAction leaveActivityAction = eventScheduler
						.scheduleRecurringEvent(ticksToEvent, this,
								TickConverter.TICKS_PER_WEEK, "leaveActivity");
				actions.add(leaveActivityAction);
			}
		}
		this.scheduledActions.put(SchedulableAction.ATTEND_ACTIVITY, actions);
	}

	/**
	 * Schedule arrivals
	 */
	@Override
	protected void scheduleArrivals() {
		EventScheduler eventScheduler = EventScheduler.getInstance();
		List<ISchedulableAction> actions = new ArrayList<>();
		List<Integer> days = this.schedule.getCampusDays();
		for (Integer day : days) {
			AcademicActivity firstActivity = this.schedule
					.getFirstAcademicActivityInDay(day);
			double arrivalTime = Randomizer.getRandomStudentArrivalTime();
			double startTime = Math.min(
					firstActivity.getStartTime() - UB_ARRIVAL_SHIFT,
					arrivalTime);
			double ticksToEvent = TickConverter.dayTimeToTicks(day, startTime);
			ISchedulableAction arriveCampusAction = eventScheduler
					.scheduleRecurringEvent(ticksToEvent, this,
							TickConverter.TICKS_PER_WEEK, "haveFun");
			actions.add(arriveCampusAction);
		}
		this.scheduledActions.put(SchedulableAction.ARRIVE_CAMPUS, actions);
	}

	/**
	 * Schedule departures
	 */
	@Override
	protected void scheduleDepartures() {
		EventScheduler eventScheduler = EventScheduler.getInstance();
		List<ISchedulableAction> actions = new ArrayList<>();
		List<Integer> days = this.schedule.getCampusDays();
		for (Integer day : days) {
			AcademicActivity lastActivity = this.schedule
					.getLastAcademicActivityInDay(day);
			double departureTime = Randomizer.getRandomStudentDepartureTime();
			double endTime = Math.max(lastActivity.getEndTime(), departureTime);
			double ticksToEvent = TickConverter.dayTimeToTicks(day, endTime);
			ISchedulableAction returnHomeAction = eventScheduler
					.scheduleRecurringEvent(ticksToEvent, this,
							TickConverter.TICKS_PER_WEEK, "returnHome");
			actions.add(returnHomeAction);
			this.scheduledDepartures.put(day, endTime);
		}
		this.scheduledActions.put(SchedulableAction.RETURN_HOME, actions);
	}

	/**
	 * Schedule in-campus lunch
	 */
	@Override
	protected void scheduleLunch() {
		EventScheduler eventScheduler = EventScheduler.getInstance();
		List<ISchedulableAction> actions = new ArrayList<>();
		List<Integer> days = this.schedule.getCampusDays();
		for (Integer day : days) {
			Pair<Double, Double> lunch = Heuristics
					.getRandomLunchTime(this.schedule, day);
			if (lunch == null) {
				continue;
			}
			double lunchTime = lunch.getFirst();
			double lunchDuration = lunch.getSecond();
			double ticksToEvent = TickConverter.dayTimeToTicks(day, lunchTime);
			ISchedulableAction haveLunchAction = eventScheduler
					.scheduleRecurringEvent(ticksToEvent, this,
							TickConverter.TICKS_PER_WEEK, "haveLunch");
			actions.add(haveLunchAction);
			ticksToEvent += lunchDuration;
			ISchedulableAction haveFunAction = eventScheduler
					.scheduleRecurringEvent(ticksToEvent, this,
							TickConverter.TICKS_PER_WEEK, "haveFun");
			actions.add(haveFunAction);
		}
		this.scheduledActions.put(SchedulableAction.HAVE_LUNCH, actions);
	}

}