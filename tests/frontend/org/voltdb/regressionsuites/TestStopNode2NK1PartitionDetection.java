/* This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.regressionsuites;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import junit.framework.Test;
import junit.framework.TestCase;

import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.compiler.DeploymentBuilder;

public class TestStopNode2NK1PartitionDetection extends RegressionSuite {
    private static final Class<? extends TestCase> TESTCASECLASS =
            TestStopNode2NK1PartitionDetection.class;

    static LocalCluster m_config;

    public TestStopNode2NK1PartitionDetection(String name) {
        super(name);
    }

    class StopCallBack implements ProcedureCallback {
        final byte m_status;
        final int m_hid;
        final CountDownLatch m_cdl;

        public StopCallBack(CountDownLatch cdl, byte status, int hid) {
            m_status = status;
            m_hid = hid;
            m_cdl = cdl;
        }
        @Override
        public void clientCallback(ClientResponse clientResponse) throws Exception {
            m_cdl.countDown();
            System.out.println("Host " + m_hid
                    + " Result : " + clientResponse.getStatusString() + " Status: " + clientResponse.getStatus());

            assertEquals(m_status, clientResponse.getStatus());
        }

    }

    public void testStopNode() throws Exception {
        Client client = getFullyConnectedClient();

        boolean lostConnect = false;
        try {
            CountDownLatch cdl = new CountDownLatch(2);
            //Stop a node that should stay up
            client.callProcedure(new StopCallBack(cdl, ClientResponse.GRACEFUL_FAILURE, 1), "@StopNode", 1);
            //Stop a node that should stay up
            client.callProcedure(new StopCallBack(cdl, ClientResponse.GRACEFUL_FAILURE, 0), "@StopNode", 0);
            VoltTable tab = client.callProcedure("@SystemInformation", "overview").getResults()[0];
            cdl.await();
        } catch (Exception pce) {
            pce.printStackTrace();
            lostConnect = pce.getMessage().contains("was lost before a response was received");
        }
        //We should never lose contact.
        assertFalse(lostConnect);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    static public Test suite() throws IOException {
        DeploymentBuilder db = new DeploymentBuilder(4, 2, 1);
        LocalCluster cluster = LocalCluster.configure(TESTCASECLASS.getSimpleName(), "", db);
        assertNotNull("LocalCluster failed to compile", cluster);
        return new MultiConfigSuiteBuilder(TESTCASECLASS, cluster);
    }
}

