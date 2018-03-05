// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL
package javax.jmdns.impl.tasks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.impl.DNSConstants;
import javax.jmdns.impl.DNSOutgoing;
import javax.jmdns.impl.DNSState;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.ServiceInfoImpl;

/** The Renewer is there to send renewal announcment when the record expire for
 * ours infos. */
public class Renewer extends TimerTask {
    /**
     * 
     */
    private final JmDNSImpl jmDNSImpl;
    /** The state of the announcer. */
    DNSState taskState = DNSState.ANNOUNCED;

    @SuppressWarnings("rawtypes")
    public Renewer(JmDNSImpl jmDNSImpl) {
        this.jmDNSImpl = jmDNSImpl;
        // Associate host to this, if it needs renewal
        if (this.jmDNSImpl.getState() == DNSState.ANNOUNCED) {
            this.jmDNSImpl.setTask(this);
        }
        // Associate services to this, if they need renewal
        synchronized (this.jmDNSImpl) {
            for (Object o : this.jmDNSImpl.getServices().values()) {
                ServiceInfoImpl info = (ServiceInfoImpl) o;
                if (info.getState() == DNSState.ANNOUNCED) {
                    info.setTask(this);
                }
            }
        }
    }

    public void start(Timer timer) {
        timer.schedule(this, DNSConstants.ANNOUNCED_RENEWAL_TTL_INTERVAL,
                DNSConstants.ANNOUNCED_RENEWAL_TTL_INTERVAL);
    }

    @SuppressWarnings("rawtypes")
    public boolean cancel() {
        // Remove association from host to this
        if (this.jmDNSImpl.getTask() == this) {
            this.jmDNSImpl.setTask(null);
        }
        // Remove associations from services to this
        synchronized (this.jmDNSImpl) {
            for (Object o : this.jmDNSImpl.getServices().values()) {
                ServiceInfoImpl info = (ServiceInfoImpl) o;
                if (info.getTask() == this) {
                    info.setTask(null);
                }
            }
        }
        return super.cancel();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void run() {
        DNSOutgoing out = null;
        try {
            // send probes for JmDNS itself
            if (this.jmDNSImpl.getState() == taskState) {
                if (out == null) {
                    out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE
                            | DNSConstants.FLAGS_AA);
                }
                this.jmDNSImpl.getLocalHost().addAddressRecords(out, false);
                this.jmDNSImpl.advanceState();
            }
            // send announces for services
            // Defensively copy the services into a local list,
            // to prevent race conditions with methods registerService
            // and unregisterService.
            List list;
            synchronized (this.jmDNSImpl) {
                list = new ArrayList(this.jmDNSImpl.getServices().values());
            }
            for (Object aList : list) {
                ServiceInfoImpl info = (ServiceInfoImpl) aList;
                synchronized (info) {
                    if (info.getState() == taskState && info.getTask() == this) {
                        info.advanceState();
                        if (out == null) {
                            out = new DNSOutgoing(
                                    DNSConstants.FLAGS_QR_RESPONSE
                                            | DNSConstants.FLAGS_AA);
                        }
                        info.addAnswers(out, DNSConstants.DNS_TTL,
                                this.jmDNSImpl.getLocalHost());
                    }
                }
            }
            if (out != null) {
                this.jmDNSImpl.send(out);
            } else {
                // If we have nothing to send, another timer taskState ahead
                // of us has done the job for us. We can cancel.
                cancel();
            }
        } catch (Throwable e) {
            this.jmDNSImpl.recover();
        }
        taskState = taskState.advance();
        if (!taskState.isAnnounced()) {
            cancel();
        }
    }
}