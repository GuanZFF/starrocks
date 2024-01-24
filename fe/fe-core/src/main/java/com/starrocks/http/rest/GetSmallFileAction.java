// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/http/rest/GetSmallFileAction.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.http.rest;

import com.google.common.base.Strings;
import com.starrocks.common.util.SmallFileMgr;
import com.starrocks.common.util.SmallFileMgr.SmallFile;
import com.starrocks.http.ActionController;
import com.starrocks.http.BaseRequest;
import com.starrocks.http.BaseResponse;
import com.starrocks.http.IllegalArgException;
import com.starrocks.server.GlobalStateMgr;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetSmallFileAction extends RestBaseAction {
    private static final Logger LOG = LogManager.getLogger(GetSmallFileAction.class);

    public GetSmallFileAction(ActionController controller) {
        super(controller);
    }

    public static void registerAction(ActionController controller) throws IllegalArgException {
        controller.registerHandler(HttpMethod.GET, "/api/get_small_file", new GetSmallFileAction(controller));
    }

    @Override
    public void execute(BaseRequest request, BaseResponse response) {
        String token = request.getSingleParameter("token");
        String fileIdStr = request.getSingleParameter("file_id");

        // check param empty
        if (Strings.isNullOrEmpty(token) || Strings.isNullOrEmpty(fileIdStr)) {
            response.appendContent("Missing parameter");
            writeResponse(request, response, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        // check token
        if (!token.equals(GlobalStateMgr.getCurrentState().getNodeMgr().getToken())) {
            response.appendContent("Invalid token");
            writeResponse(request, response, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        long fileId = -1;
        try {
            fileId = Long.valueOf(fileIdStr);
        } catch (NumberFormatException e) {
            response.appendContent("Invalid file id format: " + fileIdStr);
            writeResponse(request, response, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        SmallFileMgr fileMgr = GlobalStateMgr.getCurrentState().getSmallFileMgr();
        SmallFile smallFile = fileMgr.getSmallFile(fileId);
        if (smallFile == null || !smallFile.isContent) {
            response.appendContent("File not found or is not content");
            writeResponse(request, response, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        HttpMethod method = request.getRequest().method();
        if (method.equals(HttpMethod.GET)) {
            writeObjectResponse(request, response, HttpResponseStatus.OK, smallFile.getContentBytes(),
                    smallFile.name, true);
        } else {
            response.appendContent(new RestBaseResult("HTTP method is not allowed.").toJson());
            writeResponse(request, response, HttpResponseStatus.METHOD_NOT_ALLOWED);
        }
    }
}
