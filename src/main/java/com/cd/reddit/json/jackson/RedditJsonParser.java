/*
Copyright 2013 Cory Dissinger

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/

package com.cd.reddit.json.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import com.cd.reddit.RedditException;
import com.cd.reddit.json.mapping.RedditAccount;
import com.cd.reddit.json.mapping.RedditComment;
import com.cd.reddit.json.mapping.RedditJsonMessage;
import com.cd.reddit.json.mapping.RedditLink;
import com.cd.reddit.json.mapping.RedditMessage;
import com.cd.reddit.json.mapping.RedditMore;
import com.cd.reddit.json.mapping.RedditSubreddit;
import com.cd.reddit.json.mapping.RedditType;
import com.cd.reddit.json.util.RedditComments;
import com.cd.reddit.json.util.RedditJsonConstants;

public class RedditJsonParser {
	
	private final String json;

	private ObjectMapper mapper;	
	private JsonNode rootNode;

	
	public RedditJsonParser(String string){
		json = string;
	}
	
	public RedditJsonMessage parseJsonMessage() throws RedditException{
		init();
		
		return mapJsonMessage(rootNode.get("json"));
	}
	
	@SuppressWarnings("unchecked")
	public List<RedditAccount> parseAccounts() throws RedditException{
		init();
		
		return (List<RedditAccount>) parseSpecificType(rootNode, RedditJsonConstants.TYPE_ACCOUNT);
	}
	
	public RedditComments parseComments() throws RedditException{
		init();
		
		return mapJsonComments();
	}
	
	@SuppressWarnings("unchecked")
	public List<RedditLink> parseLinks() throws RedditException{
		init();
		
		return (List<RedditLink>) parseSpecificType(rootNode, RedditJsonConstants.TYPE_LINK);
	}
	
	@SuppressWarnings("unchecked")
	public List<RedditMessage> parseMessages() throws RedditException{
		init();
		
		return (List<RedditMessage>) parseSpecificType(rootNode, RedditJsonConstants.TYPE_MESSAGE);
	}
	
	@SuppressWarnings("unchecked")
	public List<RedditSubreddit> parseSubreddits() throws RedditException{
		init();
		
		return (List<RedditSubreddit>) parseSpecificType(rootNode, RedditJsonConstants.TYPE_SUBREDDIT);
	}	

	//****************************************************************
	//			Begin private methods, mostly for mapping
	//****************************************************************
	
	private void init() throws RedditException{
		try {
			mapper = RedditJacksonManager.INSTANCE.getObjectMapper();
			rootNode = mapper.readTree(json);
		} catch (JsonParseException e) {
			throw new RedditException(e);
		} catch (IOException e) {
			throw new RedditException(e);
		}		
	}	
	
	private List<? extends RedditType> parseSpecificType(JsonNode theNode, String specifiedType) throws RedditException{
		try {
			if(theNode.isArray()){
				Iterator<JsonNode> theEles = theNode.getElements();
				return parseManyNodes(theEles, specifiedType);
			}else{
				return parseRedditTypes(theNode, specifiedType);
			}
			
		} catch (Exception e) {
			throw new RedditException(e);
		}		
	}
	
	private List<RedditType> parseManyNodes(Iterator<JsonNode> theEles, String specifiedType) throws RedditException {
		final List<RedditType> theTypes = new ArrayList<RedditType>(20);
		
		while(theEles.hasNext()){
			final JsonNode nextNode = theEles.next();
			List<RedditType> parsedTypes = parseRedditTypes(nextNode, specifiedType);
			
			theTypes.addAll(parsedTypes);
		}
		
		return theTypes;
	}

	private List<RedditType> parseRedditTypes(JsonNode aNode, String specifiedType) throws RedditException{
		final JsonNode kindNode = aNode.get(RedditJsonConstants.KIND);
		final String theKind;
		
		if(kindNode == null){
			throw new RedditException("No kind found for node: " + aNode.toString());
		}else{
			theKind = kindNode.asText();
		}
		
		if(RedditJsonConstants.LISTING.equals(theKind)){
			final JsonNode childData = aNode.get(RedditJsonConstants.DATA).get(RedditJsonConstants.CHILDREN);
			return mapJsonArrayToList(childData, specifiedType); 
		}else{
			final JsonNode childData = aNode.get(RedditJsonConstants.DATA);
			final List<RedditType> singleType = new ArrayList<RedditType>(1);
			singleType.add(mapJsonObjectToSpecifiedType(childData, theKind, specifiedType));
			return singleType;			
		} 
	}
	
	@SuppressWarnings("unchecked")
	private RedditComments mapJsonComments() throws RedditException{
		final JsonNode parentLinkNode	= rootNode.get(0);
		final JsonNode commentsNode		= rootNode.get(1);
		final ArrayNode childrenNode  	= (ArrayNode)commentsNode.get(RedditJsonConstants.DATA)
												  .get(RedditJsonConstants.CHILDREN);
		
		final JsonNode moreNode			= childrenNode.get(childrenNode.size() - 1).get(RedditJsonConstants.DATA);
		
		//See the 'replies' attribute of RedditComment. It is a JsonNode
		final List<RedditLink> theParentLink	   = (List<RedditLink>) parseSpecificType(parentLinkNode, RedditJsonConstants.TYPE_LINK);
		final List<RedditComment> topLevelComments = (List<RedditComment>) parseSpecificType(rootNode, RedditJsonConstants.TYPE_COMMENT);
		final RedditMore theMore;
		
		try {
			theMore = mapper.readValue(moreNode, RedditMore.class);
		} catch (Exception e) {
			throw new RedditException(e);
		}
		
		return new RedditComments(theParentLink.get(0), topLevelComments, theMore);
	}
	
	private RedditJsonMessage mapJsonMessage(JsonNode jsonMessage) throws RedditException{
		RedditJsonMessage parsedMessage = new RedditJsonMessage();
		final List<String> errors = new ArrayList<String>(1);
		final Iterator<JsonNode> nodeItr = jsonMessage.get(RedditJsonConstants.ERRORS).getElements();
		
		while(nodeItr.hasNext()){
			final String error = nodeItr.next().asText();
			errors.add(error);
		}
		
		final JsonNode data = jsonMessage.get(RedditJsonConstants.DATA);
		
		parsedMessage.setErrors(errors);
		parsedMessage.setCookie(data.get(RedditJsonConstants.COOKIE).asText());
		parsedMessage.setModhash(data.get(RedditJsonConstants.MODHASH).asText());		
		
		return parsedMessage;
	}
	
	private List<RedditType> mapJsonArrayToList(JsonNode jsonArray, String specifiedType) throws RedditException{
		final List<RedditType> theTypes = new ArrayList<RedditType>(10);
		
		final Iterator<JsonNode> nodeItr = jsonArray.getElements();
		
		while(nodeItr.hasNext()){
			final JsonNode nextJson = nodeItr.next();
			final String jsonKind 	= nextJson.get(RedditJsonConstants.KIND).asText();
			
			if(RedditJsonConstants.LISTING.equals(jsonKind)){
				theTypes.addAll(mapJsonArrayToList(nextJson, specifiedType));
			}else{
				final JsonNode dataJson = nextJson.get(RedditJsonConstants.DATA);
				theTypes.add(mapJsonObjectToSpecifiedType(dataJson, jsonKind, specifiedType));	
			}
		}
		
		theTypes.removeAll(Collections.singleton(null));		
		
		return theTypes;
	}

	private RedditType mapJsonObjectToSpecifiedType(JsonNode jsonObject, String kind, String specifiedType) throws RedditException{
		RedditType theType = null;
		
		if(specifiedType.equals(kind)){
			theType = mapJsonObjectToType(jsonObject, specifiedType);				
		}
		
		return theType;
	}

	private RedditType mapJsonObjectToType(JsonNode jsonObject, String kind) throws RedditException{
		RedditType theType = null;
		
		try{
			if(RedditJsonConstants.TYPE_ACCOUNT.equals(kind)){
				theType = mapper.readValue(jsonObject, RedditAccount.class);
			}else if(RedditJsonConstants.TYPE_COMMENT.equals(kind)){
				theType = mapper.readValue(jsonObject, RedditComment.class);	
			}else if(RedditJsonConstants.TYPE_LINK.equals(kind)){
				theType = mapper.readValue(jsonObject, RedditLink.class);	
			}else if(RedditJsonConstants.TYPE_MESSAGE.equals(kind)){
				theType = mapper.readValue(jsonObject, RedditMessage.class);	
			}else if(RedditJsonConstants.TYPE_SUBREDDIT.equals(kind)){
				theType = mapper.readValue(jsonObject, RedditSubreddit.class);	
			}
		}catch(Exception e){
			throw new RedditException(e);
		}
		
		return theType;
	}	
}
