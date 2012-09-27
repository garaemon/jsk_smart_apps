package ros.android.jskandroidgui;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.hardware.SensorManager;

import org.ros.node.Node;
import org.ros.node.topic.Subscriber;
import org.ros.node.topic.Publisher;
import org.ros.node.parameter.ParameterTree;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.exception.RosException;
import org.ros.message.Time;
import org.ros.message.std_msgs.Empty;
import org.ros.message.roseus.StringStamped;
import org.ros.message.jsk_gui_msgs.Action;

import ros.android.views.JoystickView;
import ros.android.activity.RosAppActivity;
import java.util.ArrayList;
//import java.util.*;

/**
 * @author chen@jsk.t.u-tokyo.ac.jp (Haseru Azuma)
 */

public class JskAndroidGui extends RosAppActivity {
    private String robotAppName, cameraTopic;
    private SensorImageViewInfo cameraView;
    private JoystickView joystickView;
    private Publisher<Empty> GetSpotPub;
    private Publisher<StringStamped> StartDemoPub;
    private Publisher<StringStamped> MoveToSpotPub;
    private ParameterTree params;

    private Button demo_button;
    private RadioGroup radioGroup;
    private Spinner spots_spinner, tasks_spinner, image_spinner, points_spinner;
    private ArrayList<String> spots_list = new ArrayList(), tasks_list = new ArrayList();
    private String defaultCamera = "/openni/rgb", defaultPoints = "/openni/depth_registered/points";
    private boolean isDrawLine = false,isAdapterSet_spots = false, isAdapterSet_tasks = false,isNotParamInit = true;

    @Override
	public void onCreate(Bundle savedInstanceState) {
	setDefaultAppName("jsk_gui/jsk_android_gui");
	setDashboardResource(R.id.top_bar);
	setMainWindowResource(R.layout.main);
	super.onCreate(savedInstanceState);

	//demo_button = (Button)findViewById(R.id.button_demo);
	radioGroup = (RadioGroup) findViewById(R.id.radiogroup);
	radioGroup.check(R.id.radiobutton_L);
	radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
		public void onCheckedChanged(RadioGroup group, int checkedId) {
		    RadioButton radioButton = (RadioButton) findViewById(checkedId);
		    if (radioButton.getText().equals("left")){
			cameraView.SetRobotArm(Action.LARMID);
			safeToastStatus("robot arm set to :larm");
			Log.i("JskAndroidGui:ItemSeleted", "Set arm to :larm");
		    } else {
			cameraView.SetRobotArm(Action.RARMID);
			safeToastStatus("robot arm set to :rarm");
			Log.i("JskAndroidGui:ItemSeleted", "Set arm to :rarm");
		    }
		}
	    });

	spots_spinner = (Spinner)findViewById(R.id.spinner_spots);
	ArrayAdapter<String> adapter_spots = new ArrayAdapter<String>(this, R.layout.list);
	spots_spinner.setAdapter(adapter_spots);
	spots_spinner.setPromptId(R.string.SpinnerPrompt_spots);

	tasks_spinner = (Spinner)findViewById(R.id.spinner_tasks);
	ArrayAdapter<String> adapter_tasks = new ArrayAdapter<String>(this, R.layout.list);
	tasks_spinner.setAdapter(adapter_tasks);
	tasks_spinner.setPromptId(R.string.SpinnerPrompt_tasks);

	image_spinner = (Spinner)findViewById(R.id.spinner_image);
	String[] image_list = {"cameras", "/openni/rgb", "/camera/rgb", "/wide_stereo/left", "/wide_stereo/right", "/narrow_stereo/left", "/narrow_stereo/right", "/l_forearm_cam", "/r_forearm_cam"}; //Todo, get active camera list
	ArrayAdapter<String> adapter_image = new ArrayAdapter<String>(this, R.layout.list, image_list);
	image_spinner.setAdapter(adapter_image);

	points_spinner = (Spinner)findViewById(R.id.spinner_points);
	String[] points_list = {"points", "/openni/depth_registered/points", "/camera/rgb/points", "/tilt_laser_cloud2"};
	ArrayAdapter<String> adapter_points = new ArrayAdapter<String>(this, R.layout.list, points_list);
	points_spinner.setAdapter(adapter_points);

	if (getIntent().hasExtra("camera_topic")) {
	    cameraTopic = getIntent().getStringExtra("camera_topic");
	} else {
	    cameraTopic = "/openni/marked/image_rect_color/compressed_throttle";
	}
	joystickView = (JoystickView) findViewById(R.id.joystick);
	joystickView.setBaseControlTopic("android/cmd_vel");
	cameraView = (SensorImageViewInfo) findViewById(R.id.image);
	cameraView.setClickable(true);
    }

    @Override
	protected void onNodeCreate(Node node) {
	super.onNodeCreate(node);
	try {
	    NameResolver appNamespace = getAppNamespace(node);
	    cameraView.start(node, appNamespace.resolve(cameraTopic).toString());
	    cameraView.post(new Runnable() {
		    @Override
			public void run() {
			cameraView.setSelected(true);
		    }
		});
	    joystickView.start(node);
	} catch (Exception ex) {
	    Log.e("JskAndroidGui", "Init error: " + ex.toString());
	    safeToastStatus("Failed: " + ex.getMessage());
	}

	GetSpotPub =
	    node.newPublisher( "/Tablet/GetSpot" , "std_msgs/Empty" );
	StartDemoPub =
	    node.newPublisher( "/Tablet/StartDemo" , "roseus/StringStamped" );
	MoveToSpotPub =
	    node.newPublisher( "/Tablet/MoveToSpot" , "roseus/StringStamped" );

	// demo_button.setOnClickListener(new OnClickListener(){
	// 	public void onClick(View viw) {
	// 	    Button button = (Button)viw;
	// 	    // button.setText("starting");
	// 	    StringStamped StrMsg = new StringStamped();
	// 	    StrMsg.header.stamp = Time.fromMillis(System.currentTimeMillis());
	// 	    StrMsg.data = "StartMainDemo";
	// 	    StartDemoPub.publish( StrMsg );
	// 	    safeToastStatus("demo: " + "StartMainDemo");
	// 	    Log.i("JskAndroidGui:ItemSeleted", "Sending StartDemo main messgae");
	// 	}});

	params = node.newParameterTree();
	/* for spots */
	try{
	    String defaultSpot_ns = "/jsk_spots";
	    String targetSpot = "/eng2/7f"; // Todo get current targetSpot
	    GraphName param_ns = new GraphName(defaultSpot_ns + targetSpot);
	    NameResolver resolver = node.getResolver().createResolver(param_ns);
	    Object[] spots_param_list = params.getList(resolver.resolve("spots")).toArray();
	    Log.i("JskAndroidGui:GetSpotsParam", "spots length = " + spots_param_list.length);
	    spots_list.clear();spots_list.add("spots");
	    for (int i = 0; i < spots_param_list.length; i++) {
		spots_list.add((String)spots_param_list[i]);
		Log.w("JskAndroidGui:GetSpotsParam", "lists:" + i + " " + spots_param_list[i]);
	    }
	} catch (Exception ex) {
	    Log.e("JskAndroidGui", "Param cast error: " + ex.toString());
	    safeToastStatus("Failed: " + ex.getMessage());
	}
	spots_spinner.setOnItemSelectedListener(new OnItemSelectedListener(){
		public void onItemSelected(AdapterView parent, View viw, int arg2, long arg3) {
		    if(isAdapterSet_spots){
			Spinner spinner = (Spinner)parent;
			String item = (String)spinner.getSelectedItem();
			StringStamped StrMsg = new StringStamped();
			StrMsg.header.stamp = Time.fromMillis(System.currentTimeMillis());
			StrMsg.data = item;
			MoveToSpotPub.publish( StrMsg );
			safeToastStatus("spots: MoveToSpot " + item);
			Log.i("JskAndroidGui:ItemSeleted", "Sending MoveToSpot messgae");
		    } else {
			isAdapterSet_spots = true;
			Log.i("JskAndroidGui:", "spots adapter not set");
		    }
		}
		public void onNothingSelected(AdapterView parent) {
		}});
	/* for tasks */
	try{
	    String defaultTask_ns = "/Tablet";
	    GraphName gtask0 = new GraphName(defaultTask_ns);
	    NameResolver resolver0 = node.getResolver().createResolver(gtask0);
	    Object[] user_list = params.getList(resolver0.resolve("UserList")).toArray();
	    tasks_list.clear();tasks_list.add("tasks");
	    for (int i = 0; i < user_list.length; i++) {
		GraphName gtask = new GraphName(defaultTask_ns + "/User");
		NameResolver resolver = node.getResolver().createResolver(gtask);
		Object[] task_param_list = params.getList(resolver.resolve((String)user_list[i])).toArray();

		Log.i("JskAndroidGui:GetTasksParam", "task length = " + task_param_list.length);
		for (int j = 0; j < task_param_list.length; j++) {
		    Log.i("JskAndroidGui:GetTasksParam", "lists: " +  i + " " + j + " /Tablet/" + (String)user_list[i] + "/" + (String)task_param_list[j]);
		    tasks_list.add("/Tablet/" + (String)user_list[i] + "/" + (String)task_param_list[j]);
		}
	    }
	} catch (Exception ex) {
	    Log.e("JskAndroidGui", "Param cast error: " + ex.toString());
	    safeToastStatus("Failed: " + ex.getMessage());
	}

	tasks_spinner.setOnItemSelectedListener(new OnItemSelectedListener(){
		public void onItemSelected(AdapterView parent, View viw, int arg2, long arg3) {
		    if(isAdapterSet_tasks){
			Spinner spinner = (Spinner)parent;
			String item = (String)spinner.getSelectedItem();
			StringStamped StrMsg = new StringStamped();
			StrMsg.header.stamp = Time.fromMillis(System.currentTimeMillis());
			StrMsg.data = item;
			StartDemoPub.publish( StrMsg );
			safeToastStatus("tasks: StartDemo " + item);
			Log.i("JskAndroidGui:ItemSeleted", "Sending StartDemo messgae");
		    } else {
			isAdapterSet_tasks = true;
			Log.i("JskAndroidGui:", "tasks adapter not set");
		    }
		}
		public void onNothingSelected(AdapterView parent) {
		}});

	/* for camera */
	image_spinner.setOnItemSelectedListener(new OnItemSelectedListener(){
		public void onItemSelected(AdapterView parent, View viw, int arg2, long arg3) {
		    Spinner spinner = (Spinner)parent;
		    defaultCamera = (String)spinner.getSelectedItem();
		    String str =  "((:image "+ defaultCamera + ") (:points " + defaultPoints + "))";
		    cameraView.PubSwitchSensor(str);
		    safeToastStatus("SwitchSensor: " + str);
		    Log.i("JskAndroidGui:ItemSeleted", "Sending switch messgae");
		}
		public void onNothingSelected(AdapterView parent) {
		}});

	/* for points */
	points_spinner.setOnItemSelectedListener(new OnItemSelectedListener(){
		public void onItemSelected(AdapterView parent, View viw, int arg2, long arg3) {
		    Spinner spinner = (Spinner)parent;
		    defaultPoints = (String)spinner.getSelectedItem();
		    String str =  "((:image "+ defaultCamera + ") (:points " + defaultPoints + "))";
		    cameraView.PubSwitchSensor(str);
		    safeToastStatus("SwitchSensor: " + str);
		    Log.i("JskAndroidGui:ItemSeleted", "Sending switch messgae");
		}
		public void onNothingSelected(AdapterView parent) {
		}});
    }

    @Override
	protected void onNodeDestroy(Node node) {
	super.onNodeDestroy(node);
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.jsk_android_gui, menu);
	GetParamAndSetSpinner();
	return true;
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	switch (item.getItemId()) {
	case R.id.getspot:
	    Empty EmptyMsg = new Empty();
	    GetSpotPub.publish( EmptyMsg );
	    Log.i("JskAndroidGui:ItemSeleted", "Sending GetSpot messgae");
	    return true;
	case R.id.setdrawline:
	    if (isDrawLine) {
		Log.i("JskAndroidGui:ItemSeleted", "unSet DrawLine");
		cameraView.unSetDrawLine();
		isDrawLine = false;
	    } else {
		Log.i("JskAndroidGui:ItemSeleted", "Set DrawLine");
		cameraView.SetDrawLine();
		isDrawLine = true;
	    }
	    return true;
	case R.id.pickonce:
	    Log.i("JskAndroidGui:ItemSeleted", "Set PickOnce");
	    cameraView.SetPickOnce();
	    return true;
	case R.id.opendoor:
	    cameraView.unSetMovingFingerInfo();
	    cameraView.SendOpenDoorMsg();
	    Log.i("JskAndroidGui:ItemSeleted", "Send OpenDoorMsg");
	    return true;
	case R.id.pushonce:
	    Log.i("JskAndroidGui:ItemSeleted", "Set PushOnce");
	    cameraView.SetPushOnce();
	    return true;
	case R.id.placeonce:
	    Log.i("JskAndroidGui:ItemSeleted", "Set PlaceOnce");
	    cameraView.SetPlaceOnce();//
	    return true;
	case R.id.closedoor:
	    Log.i("JskAndroidGui:ItemSeleted", "Send CloseDoorMsg");
	    cameraView.SendCloseDoorMsg();//
	    return true;

	case R.id.passtohumanonce:
	    Log.i("JskAndroidGui:ItemSeleted", "Set PassToHuman");
	    cameraView.SetPassToHumanOnce();//
	    return true;

	case R.id.tuckarmpose:
	    Log.i("JskAndroidGui:ItemSeleted", "TuckArmPose");
	    cameraView.SendTuckArmPoseMsg();//
	    return true;
	case R.id.torsoup: //DEPRECATED
	    cameraView.SendTorsoUpMsg();//
	    Log.i("JskAndroidGui:ItemSeleted", "Send TorsoUpMsg");
	    return true;
	case R.id.torsodown: //DEPRECATED
	    cameraView.SendTorsoDownMsg();//
	    Log.i("JskAndroidGui:ItemSeleted", "Send TorsoDownMsg");
	    return true;
	case R.id.opengripper: //DEPRECATED
	    cameraView.SendOpenGripperMsg();
	    Log.i("JskAndroidGui:ItemSeleted", "Send OpenGripperMsg");
	    return true;
	case R.id.closegripper: //DEPRECATED
	    cameraView.SendCloseGripperMsg();
	    Log.i("JskAndroidGui:ItemSeleted", "Send CloseGripperMsg");
	    return true;
	case R.id.changetouchmode:
	    cameraView.ChangeTouchMode();
	    Log.i("JskAndroidGui:ItemSeleted", "Change TouchMode");
	    return true;
	case R.id.resetall:
	    isAdapterSet_spots = false; isAdapterSet_tasks = false;
	    GetParamAndSetSpinner();
	    cameraView.SetResetAll();
	    isDrawLine = false;
	    Log.i("JskAndroidGui:ItemSeleted", "Set ResetAll");
	    return true;
	default:
	    return super.onOptionsItemSelected(item);
	}
    }

    protected void GetParamAndSetSpinner() {
	//tasks_list.clear(); spots_list.clear();
	ArrayAdapter<String> adapter_spots = new ArrayAdapter<String>(this, R.layout.list);
	for (int i = 0; i <= spots_list.size() - 1; i++) {
	    adapter_spots.add(spots_list.get(i));
	}
	spots_spinner.setAdapter(adapter_spots);
	ArrayAdapter<String> adapter_tasks = new ArrayAdapter<String>(this, R.layout.list);
	for (int i = 0; i <= tasks_list.size() - 1; i++) {
	    adapter_tasks.add(tasks_list.get(i));
	}
	tasks_spinner.setAdapter(adapter_tasks);
    }
}
