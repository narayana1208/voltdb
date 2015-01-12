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

package org.voltdb;

import java.io.File;
import java.io.IOException;

import org.voltdb.VoltDB.Configuration;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ProcCallException;
import org.voltdb.compiler.DeploymentBuilder;
import org.voltdb.compiler.DeploymentBuilder.UserInfo;

public class TestAdhocCreateDropRole extends AdhocDDLTestBase {

    static Class<?>[] PROC_CLASSES = { org.voltdb_testprocs.updateclasses.testImportProc.class,
        org.voltdb_testprocs.updateclasses.testCreateProcFromClassProc.class,
        org.voltdb_testprocs.updateclasses.InnerClassesTestProc.class };

    public void testBasic() throws Exception
    {
        System.out.println("\n\n-----\n testBasic \n-----\n\n");
        String literalSchema =
                "create table FOO (" +
                "ID integer not null," +
                "VAL bigint, " +
                "constraint PK_TREE primary key (ID)" +
                ");\n" +
                "partition table FOO on column ID;\n" +

                "create table FOO_R (" +
                "ID integer not null," +
                "VAL bigint, " +
                "constraint PK_TREE_R primary key (ID)" +
                ");\n" +
                "";
        DeploymentBuilder db = new DeploymentBuilder(2)
        .setUseAdHocDDL(true)
        .setSecurityEnabled(true, false)
        // Use random caps in role names to check case-insensitivity
        .addUsers(new DeploymentBuilder.UserInfo("admin", "admin", "Administrator"))
        ;
        Configuration config = Configuration.compile(getClass().getSimpleName(), literalSchema, db);
        assertNotNull("Configuration failed to compile", config);

        try {
            startServer(config);
            ClientConfig adminConfig = new ClientConfig("admin", "admin");
            Client adminClient = ClientFactory.createClient(adminConfig);
            ClientConfig userConfig = new ClientConfig("user", "user");
            Client userClient = ClientFactory.createClient(userConfig);

            adminClient.createConnection("localhost");
            // Can't connect a user which doesn't exist
            boolean threw = false;
            try {
                userClient.createConnection("localhost");
            }
            catch (IOException ioe) {
                threw = true;
                assertTrue(ioe.getMessage().contains("Authentication rejected"));
            }
            assertTrue("Connecting bad user should have failed", threw);

            db.addUsers(new UserInfo("user", "user", "NEWROLE"));
            String pathToDeployment = db.writeXMLToTempFile();
            try {
                adminClient.updateApplicationCatalog(null, new File(pathToDeployment));
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("Should be able to add a user even with a role that doesn't exist");
            }

            // Check that we can connect the new user
            try {
                userClient.createConnection("localhost");
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
                fail("Should have been able to connect 'user'");
            }

            // Make sure the user doesn't actually have DEFAULTPROC permissions yet
            threw = false;
            try {
                userClient.callProcedure("FOO.insert", 0, 0);
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                threw = true;
            }
            assertTrue("'user' shouldn't be able to call procedures yet", threw);

            // Okay, it's showtime.  Let's add the role through live DDL
            try {
                adminClient.callProcedure("@AdHoc", "create role NEWROLE with DEFAULTPROC");
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("Creating role should have succeeded");
            }

            try {
                adminClient.updateApplicationCatalog(null, new File(pathToDeployment));
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("Adding 'user' should have succeeded this time");
            }

            // Make sure the user now has DEFAULTPROC permissions
            try {
                userClient.callProcedure("FOO.insert", 0, 0);
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("'user' should be able to call default procs now");
            }

            threw = false;
            try {
                adminClient.callProcedure("@AdHoc", "create role NEWROLE with ALLPROC");
            }
            catch (ProcCallException pce) {
                assertTrue(pce.getMessage().contains("already exists"));
                threw = true;
            }
            assertTrue("Shouldn't be able to 'create' same role twice", threw);

            threw = false;
            try {
                // Use random caps in role names to check case-insensitivity
                adminClient.callProcedure("@AdHoc", "create role aDministrator with ALLPROC");
            }
            catch (ProcCallException pce) {
                assertTrue(pce.getMessage().contains("already exists"));
                threw = true;
            }
            assertTrue("Shouldn't be able to 'create' ADMINISTRATOR role", threw);

            threw = false;
            try {
                adminClient.callProcedure("@AdHoc", "create role USER with ALLPROC");
            }
            catch (ProcCallException pce) {
                assertTrue(pce.getMessage().contains("already exists"));
                threw = true;
            }
            assertTrue("Shouldn't be able to 'create' USER role", threw);

            try {
                adminClient.callProcedure("@AdHoc", "drop role NEWROLE;");
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("Should be able to drop role NEWROLE");
            }

            // Can't drop twice
            try {
                adminClient.callProcedure("@AdHoc", "drop role NEWROLE;");
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                threw = true;
            }
            assertTrue("Can't vanilla DROP a role which doesn't exist", threw);

            // unless you use IF EXISTS
            try {
                adminClient.callProcedure("@AdHoc", "drop role NEWROLE if exists;");
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                fail("Should be able to drop role NEWROLE if exists");
            }

            // Make sure the user doesn't actually have DEFAULTPROC permissions any more
            threw = false;
            try {
                userClient.callProcedure("FOO.insert", 0, 0);
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                threw = true;
            }
            assertTrue("'user' shouldn't be able to call procedures yet", threw);

            threw = false;
            try {
                adminClient.callProcedure("@AdHoc", "drop role USER;");
            }
            catch (ProcCallException pce) {
                threw = true;
                assertTrue(pce.getMessage().contains("You may not drop the built-in role"));
                pce.printStackTrace();
            }
            assertTrue("Shouldn't be able to drop role USER", threw);

            // CHeck the administrator error message, there should end up being multiple
            // reasons why we can't get rid of this role (like, we will require you to always
            // have a user with this role)
            threw = false;
            try {
                // Use random caps in role names to check case-insensitivity
                adminClient.callProcedure("@AdHoc", "drop role adMinistrator;");
            }
            catch (ProcCallException pce) {
                threw = true;
                assertTrue(pce.getMessage().contains("You may not drop the built-in role"));
                pce.printStackTrace();
            }
            assertTrue("Shouldn't be able to drop role ADMINISTRATOR", threw);

            // Make sure that we can't get rid of the administrator user
            db.removeUser("admin");
            pathToDeployment = db.writeXMLToTempFile();
            threw = false;
            try {
                adminClient.updateApplicationCatalog(null, new File(pathToDeployment));
            }
            catch (ProcCallException pce) {
                pce.printStackTrace();
                threw = true;
            }
            assertTrue("Shouldn't be able to remove the last remaining ADMINSTRATOR user", threw);
        }
        finally {
            teardownSystem();
        }
    }
}
