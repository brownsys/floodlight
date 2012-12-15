package net.floodlightcontroller.benchmarkcontroller;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openflow.protocol.OFBarrierReply;
import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkController implements IOFSwitchListener, IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected Set macAddresses;
	protected static Logger logger;
	protected IStaticFlowEntryPusherService staticFlowEntryPusher;
	protected int flowCount = 0;
	protected Random ran = new Random();
	protected boolean stop = false;
	protected final int TOTAL_FLOWS = 500;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "BenchmarkTracker";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		macAddresses = new ConcurrentSkipListSet<Long>();
		logger = LoggerFactory.getLogger(BenchmarkController.class);
		staticFlowEntryPusher = context.getServiceImpl(IStaticFlowEntryPusherService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.BARRIER_REPLY, this);
		floodlightProvider.addOFSwitchListener(this);
	}

	private void sendBarrier(IOFSwitch sw, FloodlightContext cntx) {
		try {
			OFMessage m = new OFBarrierRequest();
			sw.write(m, cntx);
			logger.info("sending another barrier request " + m.getXid());
			logger.info("--------------------------------------------");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void pushFlow(String dp) {
		//generate a random flow
		String srcIp = IPv4.fromIPv4Address(flowCount);
		String dstIp = IPv4.fromIPv4Address(flowCount * 2);
		ArrayList<OFAction> actionsTo = new ArrayList<OFAction>();
		OFFlowMod flowMod = new OFFlowMod();
		flowMod.setType(OFType.FLOW_MOD);
		OFAction outputTo = new OFActionOutput((short) ran.nextInt(30));
		actionsTo.add(outputTo);
		OFMatch match = new OFMatch();
		match.setNetworkDestination(IPv4.toIPv4Address(dstIp));
		match.setNetworkSource(IPv4.toIPv4Address(srcIp));
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		flowMod.setActions(actionsTo);
		flowMod.setMatch(match);
		flowMod.setHardTimeout((short)20);
		logger.info("pushing the flow:" + ("Flow#" + flowCount
				+ " srcIp:" + match.getNetworkSource()
				+ " srcPort:" + match.getTransportSource()
				+ " dstIp:" + match.getNetworkDestination()
				+ " dstPort:" + match.getTransportDestination()));
		staticFlowEntryPusher.addFlow(("Flow#" + flowCount), flowMod, dp);
		flowCount ++;
		if(flowCount == TOTAL_FLOWS)
			stop = true;
	}

	private void pushMutipleFlows(int n, String dp) {
		for(int i = 0;i<n;i++) {
			pushFlow(dp);
		}
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		logger.info("this message is from:" + sw.getId());

		switch (msg.getType()) {
		case BARRIER_REPLY:
			if(!stop) {
				OFBarrierReply br = (OFBarrierReply)msg;
				logger.info("received barrier reply " + br.getXid());
				//pushFlow(HexString.toHexString(sw.getId()));
				pushMutipleFlows(100, HexString.toHexString(sw.getId()));
				sendBarrier(sw, cntx);
			}
			break;

		default:
			System.out.println("a message unknow" + msg.getType());

		};
		return Command.CONTINUE;
	}

	@Override
	public void addedSwitch(IOFSwitch sw) {
		logger.info("added a new switch:" + sw.getId()
				+ " with "
				+ sw.getPorts().size()
				+ " ports total, and "
				+ sw.getEnabledPorts().size()
				+ " enabled ports");

		//pushFlow(HexString.toHexString(sw.getId()));
		sendBarrier(sw, null);
	}

	@Override
	public void removedSwitch(IOFSwitch sw) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchPortChanged(Long switchId) {
		// TODO Auto-generated method stub
	}
}
