#!/bin/sh
#####################################################################
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
# http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#####################################################################
#
# This file is only for building a development environment to
# make the rmi work with self-signed certifications.
#
#####################################################################


keytool -delete -alias rmiclient -storepass changeit -keystore ofbizcerts.jks -storepass changeit
keytool -genkey -alias rmiclient -keyalg RSA -keystore ofbizcerts.jks -storepass changeit -storetype jks -keypass changeit -dname "CN=rmiclient, OU=OFBiz, O=Beijing Langhua Ltd., L=Haidian, S=Beijing, C=CN"

keytool -delete -alias rmissl -storepass changeit -keystore ofbizrmi.jks
keytool -genkey -alias rmissl -keyalg RSA -keystore ofbizrmi.jks -storepass changeit -storetype jks -keypass changeit -dname "CN=localhost, OU=OFBiz, O=Beijing Langhua Ltd., L=Haidian, S=Beijing, C=CN"

keytool -certreq -keyalg RSA -alias rmiclient -file rmiclient.req -keystore ofbizcerts.jks -storepass changeit
keytool -certreq -keyalg RSA -alias rmissl -file rmiserver.req -keystore ofbizrmi.jks -storepass changeit

echo "Now please sign the two req and name the signed certs in rmiclient.der and rmiserver.der."
echo "The ca certs should be in the name of cacert.der."
echo "And then run importcerts.sh."

