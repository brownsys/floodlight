package net.floodlightcontroller.benchmarkcontroller;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFBarrierReply;
import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
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

public class BenchmarkController implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected Set macAddresses;
	protected static Logger logger;
	protected IStaticFlowEntryPusherService staticFlowEntryPusher;

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
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);	
		floodlightProvider.addOFMessageListener(OFType.BARRIER_REPLY, this);	
	}

	boolean triggered = false;
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		logger.info("this message is from:" + sw.getId());

		switch (msg.getType()) {
		case PACKET_IN:
			if (!triggered) {
				String dp = "00:00:00:00:00:00:00:01";
				OFPacketIn pi = (OFPacketIn) msg;
				OFMatch match = new OFMatch();
				match.loadFromPacket(pi.getPacketData(), (short) 0);
				System.out.println(match);
				System.out.println(IPv4.fromIPv4Address(match.getNetworkDestination()));
				//IPv4-To
				List actionsTo = new ArrayList();
				// Declare the flow
				OFFlowMod fmTo = new OFFlowMod();
				fmTo.setType(OFType.FLOW_MOD);
				// Declare the action
				OFAction outputTo = new OFActionOutput((short) 2);
				actionsTo.add(outputTo);
				// Declare the match
				OFMatch mTo = new OFMatch();
				mTo.setNetworkDestination(IPv4.toIPv4Address("10.0.0.3"));
				mTo.setDataLayerType(Ethernet.TYPE_IPv4);
				fmTo.setActions(actionsTo);
				fmTo.setMatch(mTo);
				// Push the flow
				staticFlowEntryPusher.addFlow("FlowTo", fmTo, dp);
				logger.info("pushing the flow");
				System.out.println(mTo);

				try {
					OFMessage m = new OFBarrierRequest();
					sw.write(m, cntx);
					logger.info("send the first barrier request " + m.getXid());
				} catch (IOException e) {
					e.printStackTrace();
				}
				triggered = true;
			}
			break;

		case BARRIER_REPLY:
			OFBarrierReply br = (OFBarrierReply)msg;
			System.out.println(br.getType() + " " + br.getXid());
			logger.info("received barrier reply " + br.getXid());
			
			try {
				OFMessage m = new OFBarrierRequest();
				sw.write(m, cntx);
				logger.info("sending another barrier request " + m.getXid());
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;

		default:
			System.out.println("a message unknow" + msg.getType());

		};
		return Command.CONTINUE;
	}
}
