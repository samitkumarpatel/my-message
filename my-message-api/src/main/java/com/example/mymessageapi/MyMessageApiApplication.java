package com.example.mymessageapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.annotation.Collation;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

import static java.util.Objects.nonNull;

@SpringBootApplication
@EnableReactiveMongoAuditing
public class MyMessageApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyMessageApiApplication.class, args);
	}

}

enum MessageType {
	REPLY,FEED
}

record Audit(
		@CreatedDate LocalDateTime createOn,
		@LastModifiedDate LocalDateTime updateOn,
		@CreatedBy String createBy, @LastModifiedBy String modifiedBy) {}

@Collation
record Users(@Id String id, String name, Audit audit) {}
@Collation
record Feed(@Id String id,
			@DocumentReference(lazy = true ) Message message,
			Audit audit) {}
@Collation
record Message(@Id String id,
			   MessageType messageType,
			   String text,
			   @DocumentReference List<Message> replyIds,
			   Audit audit) {}

interface UsersRepository extends ReactiveMongoRepository<Users, String> {}
interface FeedRepository extends ReactiveMongoRepository<Feed, String> {}
interface MessageRepository extends ReactiveMongoRepository<Message, String> {}

@Configuration
@RequiredArgsConstructor
class Routers {
	final FeedServices feedServices;
	final MessageServices messageServices;

	@Bean
	public RouterFunction routerFunction() {
		return RouterFunctions
				.route()
				.path("/users", builder -> builder
						.GET("", request -> ServerResponse.noContent().build())
						.POST("", request -> ServerResponse.noContent().build())
						.PUT("", request -> ServerResponse.noContent().build())
						.DELETE("", request -> ServerResponse.noContent().build())
				)
				.path("/feed", builder -> builder
						.GET("", this::fetchFeeds)
						.GET("/{id}", this::fetchFeedById)
						.POST("", this::persistFeed)
						.PUT("", request -> ServerResponse.noContent().build())
						.DELETE("", request -> ServerResponse.noContent().build())
				)
				.path("/message", builder -> builder
						.GET("", this::findAllMessage)
						.GET("/{id}", this::findMessageById)
						.POST("", this::persistMessage)
						.PUT("/{parentMessageId}/reply", this::persistReplyMessage)
						.PUT("", request -> ServerResponse.noContent().build())
						.DELETE("", request -> ServerResponse.noContent().build())
				)
				.build();
	}

	private Mono<ServerResponse> findAllMessage(ServerRequest request) {
		return ServerResponse.ok().body(messageServices.finaAll(), Message.class);

	}

	private Mono<ServerResponse> findMessageById(ServerRequest request) {
		return messageServices
				.findById(request.pathVariable("id"))
				.flatMap(message -> ServerResponse.ok().bodyValue(message));
	}

	private Mono<ServerResponse> persistReplyMessage(ServerRequest request) {
		var parentMessageId = request.pathVariable("parentMessageId");
		return request
				.bodyToMono(Message.class)
				.flatMap(message -> messageServices.persistReplyMessage(parentMessageId, message))
				.flatMap(message -> ServerResponse.ok().bodyValue(message));
	}

	private Mono<ServerResponse> fetchFeedById(ServerRequest request) {
		return feedServices
				.fetchById(request.pathVariable("id"))
				.flatMap(feed -> ServerResponse.ok().bodyValue(feed));
	}

	private Mono<ServerResponse> fetchFeeds(ServerRequest request) {
		return ServerResponse.ok().body(feedServices.fetchAll(), Feed.class);
	}

	private Mono<ServerResponse> persistMessage(ServerRequest request) {
		return request
				.bodyToMono(Message.class)
				.flatMap(message -> messageServices.saveMessage(message))
				.flatMap(unused -> ServerResponse.ok().bodyValue(unused));
	}

	private Mono<ServerResponse> persistFeed(ServerRequest request) {
		return request
				.bodyToMono(Feed.class)
				//persist message first
				.flatMap(feed -> messageServices
							.saveMessage(feed.message())
							.map(message -> new Feed(feed.id(), message, feed.audit()))
				)
				//then persist feed
				.flatMap(feed -> feedServices.saveFeed(feed))
				.flatMap(unused -> ServerResponse.ok().bodyValue(unused));

	}
}

@Service
@RequiredArgsConstructor
class FeedServices {
	final FeedRepository feedRepository;

	public Mono<Feed> saveFeed(Feed feed) {
		return feedRepository
				.save(feed);
	}

	public Flux<Feed> fetchAll() {
		return feedRepository
				.findAll();
	}

	public Mono<Feed> fetchById(String id) {
		return feedRepository
				.findById(id);
	}
}

@Service
@RequiredArgsConstructor
@Slf4j
class MessageServices {
	final MessageRepository messageRepository;

	public Mono<Message> saveMessage(Message message) {
		return messageRepository
				.save(message);
	}

	public Mono<Message> updateMessage(String id, Message message) {
		return messageRepository
				.findById(id)
				.map(message1 -> new Message(message1.id(),message.messageType(),message.text(),message.replyIds(), message.audit()));
	}

	public Mono<Message> findById(String id) {
		return messageRepository
				.findById(id);
	}
	public Mono<Message> persistReplyMessage(String parentMessageId, Message message) {
		// persist the new message first
		return saveMessage(message)
				.flatMap(message1 -> findById(parentMessageId)
						// then map it to parent message reply
						.map(parentMessage -> {
							log.info("{}", parentMessage);
							//TODO check if we have good way of doing this
							var replies = nonNull(parentMessage.replyIds()) ? parentMessage.replyIds() : new ArrayList<Message>();
							replies.add(message1);
							return new Message(parentMessage.id(), parentMessage.messageType(), parentMessage.text(), replies, parentMessage.audit());
						})
						.flatMap(messageRepository::save));

	}

	public Flux<Message> finaAll() {
		return messageRepository
				.findAll();
	}
}
