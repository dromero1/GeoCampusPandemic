package simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.opengis.feature.simple.SimpleFeature;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import config.Paths;
import gis.GISCampus;
import gis.GISEatingPlace;
import gis.GISInOut;
import gis.GISLimbo;
import gis.GISOtherFacility;
import gis.GISParkingLot;
import gis.GISSharedArea;
import gis.GISTeachingFacility;
import model.Group;
import model.Heuristics;
import model.Student;
import repast.simphony.context.Context;
import repast.simphony.context.space.gis.GeographyFactory;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;
import source.Reader;

public class SimulationBuilder implements ContextBuilder<Object> {

	/**
	 * Teaching facilities
	 */
	private HashMap<String, GISTeachingFacility> teachingFacilities;

	/**
	 * Shared areas
	 */
	private HashMap<String, GISSharedArea> sharedAreas;

	/**
	 * Eating places
	 */
	private HashMap<String, GISEatingPlace> eatingPlaces;

	/**
	 * Other facilities
	 */
	private HashMap<String, GISOtherFacility> otherFacitilies;

	/**
	 * In-Out spots
	 */
	private HashMap<String, GISInOut> inOuts;

	/**
	 * Parking lots
	 */
	private HashMap<String, GISParkingLot> parkingLots;

	/**
	 * Limbo
	 */
	private GISLimbo limbo;

	/**
	 * Routes
	 */
	private Graph<String, DefaultWeightedEdge> routes;

	/**
	 * Build simulation
	 * 
	 * @param context Simulation context
	 */
	@Override
	public Context<Object> build(Context<Object> context) {
		context.setId("universityDynamicsABMS");

		// Create geography projection
		Geography<Object> geography = getGeographyProjection(context);

		// Initialize campus
		GISCampus campus = readCampus();
		campus.setGeometryInGeography(geography);
		context.add(campus);

		// Initialize teaching facilities
		this.teachingFacilities = readTeachingFacilities();
		for (GISTeachingFacility teachingFacility : this.teachingFacilities.values()) {
			teachingFacility.setGeometryInGeography(geography);
			context.add(teachingFacility);
		}

		// Initialize shared areas
		this.sharedAreas = readSharedAreas();
		for (GISSharedArea sharedArea : this.sharedAreas.values()) {
			sharedArea.setGeometryInGeography(geography);
			context.add(sharedArea);
		}

		// Initialize eating places
		this.eatingPlaces = readEatingPlaces();
		for (GISEatingPlace eatingPlace : this.eatingPlaces.values()) {
			eatingPlace.setGeometryInGeography(geography);
			context.add(eatingPlace);
		}

		// Initialize other facilities
		this.otherFacitilies = readOtherFacilities();
		for (GISOtherFacility otherFacility : this.otherFacitilies.values()) {
			otherFacility.setGeometryInGeography(geography);
			context.add(otherFacility);
		}

		// Initialize parking lots
		this.parkingLots = readParkingLots();
		for (GISParkingLot parkingLot : this.parkingLots.values()) {
			parkingLot.setGeometryInGeography(geography);
			context.add(parkingLot);
		}

		// Initialize limbo
		this.limbo = readLimbo();
		limbo.setGeometryInGeography(geography);
		context.add(limbo);

		// Initialize in-outs spots
		this.inOuts = readInOuts();
		for (GISInOut inOut : inOuts.values()) {
			inOut.setGeometryInGeography(geography);
			context.add(inOut);
		}

		// Read groups
		ArrayList<Group> groups = Reader.readGroupsDatabase(Paths.GROUPS_DATABASE);

		// Read routes
		this.routes = Reader.readRoutes(Paths.ROUTES_DATABASE);

		// Add students to simulation
		ArrayList<Student> students = createStudents(4000, geography);
		for (Student student : students) {
			student.setSchedule(Heuristics.getRandomSchedule(groups));
			student.planWeeklyEvents();
			context.add(student);
		}

		return context;
	}

	public GISLimbo getLimbo() {
		return this.limbo;
	}

	public HashMap<String, GISInOut> getInOuts() {
		return this.inOuts;
	}

	public HashMap<String, GISTeachingFacility> getTeachingFacilities() {
		return this.teachingFacilities;
	}

	public HashMap<String, GISEatingPlace> getEatingPlaces() {
		return this.eatingPlaces;
	}

	public HashMap<String, GISSharedArea> getSharedAreas() {
		return this.sharedAreas;
	}

	public Graph<String, DefaultWeightedEdge> getRoutes() {
		return this.routes;
	}
	
	private Geography<Object> getGeographyProjection(Context<Object> context) {
		GeographyParameters<Object> params = new GeographyParameters<Object>();
		GeographyFactory geographyFactory = GeographyFactoryFinder.createGeographyFactory(null);
		Geography<Object> geography = geographyFactory.createGeography("campus", context, params);
		return geography;
	}

	private GISCampus readCampus() {
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.CAMPUS_GEOMETRY_SHAPEFILE);
		Geometry geometry = (MultiPolygon) features.get(0).getDefaultGeometry();
		return new GISCampus(geometry);
	}

	private GISLimbo readLimbo() {
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.LIMBO_GEOMETRY_SHAPEFILE);
		Geometry geometry = (MultiPolygon) features.get(0).getDefaultGeometry();
		return new GISLimbo(geometry);
	}

	private HashMap<String, GISInOut> readInOuts() {
		HashMap<String, GISInOut> inOuts = new HashMap<String, GISInOut>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.INOUTS_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			double area = RandomHelper.nextDoubleFromTo(0, 20);
			GISInOut inOut = new GISInOut(id, geometry, area);
			inOuts.put(id, inOut);
		}
		return inOuts;
	}

	private HashMap<String, GISTeachingFacility> readTeachingFacilities() {
		HashMap<String, GISTeachingFacility> teachingFacilities = new HashMap<String, GISTeachingFacility>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.TEACHING_FACILITIES_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			double area = RandomHelper.nextDoubleFromTo(100, 1000);
			GISTeachingFacility teachingFacility = new GISTeachingFacility(id, geometry, area);
			teachingFacilities.put(id, teachingFacility);
		}
		return teachingFacilities;
	}

	private HashMap<String, GISSharedArea> readSharedAreas() {
		HashMap<String, GISSharedArea> sharedAreas = new HashMap<String, GISSharedArea>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.SHARED_AREAS_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			double area = RandomHelper.nextDoubleFromTo(10, 400);
			GISSharedArea sharedArea = new GISSharedArea(id, geometry, area);
			sharedAreas.put(id, sharedArea);
		}
		return sharedAreas;
	}

	private HashMap<String, GISEatingPlace> readEatingPlaces() {
		HashMap<String, GISEatingPlace> eatingPlaces = new HashMap<String, GISEatingPlace>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.EATING_PLACES_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			double area = RandomHelper.nextDoubleFromTo(100, 1000);
			GISEatingPlace eatingPlace = new GISEatingPlace(id, geometry, area);
			eatingPlaces.put(id, eatingPlace);
		}
		return eatingPlaces;
	}

	private HashMap<String, GISOtherFacility> readOtherFacilities() {
		HashMap<String, GISOtherFacility> otherFacilities = new HashMap<String, GISOtherFacility>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.OTHER_FACILITIES_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			GISOtherFacility otherFacility = new GISOtherFacility(id, geometry);
			otherFacilities.put(id, otherFacility);
		}
		return otherFacilities;
	}

	private HashMap<String, GISParkingLot> readParkingLots() {
		HashMap<String, GISParkingLot> parkingLots = new HashMap<String, GISParkingLot>();
		List<SimpleFeature> features = Reader.loadGeometryFromShapefile(Paths.PARKING_LOTS_GEOMETRY_SHAPEFILE);
		for (SimpleFeature feature : features) {
			Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
			String id = (String) feature.getAttribute(1);
			GISParkingLot parkingLot = new GISParkingLot(id, geometry);
			parkingLots.put(id, parkingLot);
		}
		return parkingLots;
	}

	private ArrayList<Student> createStudents(int studentCount, Geography<Object> geography) {
		ArrayList<Student> students = new ArrayList<Student>();
		for (int i = 0; i < studentCount; i++) {
			Student student = new Student(geography, this);
			students.add(student);
		}
		return students;
	}

}