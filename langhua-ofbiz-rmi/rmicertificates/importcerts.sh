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
# Ref: http://www-128.ibm.com/developerworks/edu/j-dw-javajsse-i.html
#
#####################################################################

# please run createcsr.sh first

keytool -delete -alias minica -storepass changeit -keystore ofbiztrust.jks -storepass changeit
keytool -import -noprompt -alias minica -keystore ofbiztrust.jks -file cacert.der -storepass changeit

keytool -delete -alias minica -storepass changeit -keystore ofbizrmi.jks -storepass changeit
keytool -import -alias minica -file cacert.der -keystore ofbizrmi.jks -storepass changeit
keytool -import -trustcacerts -noprompt -alias rmissl -keystore ofbizrmi.jks -file rmiserver.der -storepass changeit


keytool -delete -alias minica -storepass changeit -keystore ofbizcerts.jks -storepass changeit
keytool -import -alias minica -file cacert.der -keystore ofbizcerts.jks -storepass changeit
keytool -import -trustcacerts -noprompt -alias rmiclient -keystore ofbizcerts.jks -file rmiclient.der -storepass changeit

keytool -list -v -keystore ofbizrmi.jks -storepass changeit
keytool -list -v -keystore ofbizcerts.jks -storepass changeit
