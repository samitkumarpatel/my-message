package com.example.mymessageapi;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.annotation.Collation;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

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
			@DBRef Message messageId,
			Audit audit) {}
@Collation
record Message(@Id String id,
			   MessageType messageType,
			   String text,
			   @DBRef List<Message> replyIds,
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
						.GET("", request -> ServerResponse.noContent().build())
						.POST("", this::persistFeed)
						.PUT("", request -> ServerResponse.noContent().build())
						.DELETE("", request -> ServerResponse.noContent().build())
				)
				.path("/message", builder -> builder
						.GET("", request -> ServerResponse.noContent().build())
						.POST("", this::persistMessage)
						.PUT("", request -> ServerResponse.noContent().build())
						.DELETE("", request -> ServerResponse.noContent().build())
				)
				.build();
	}

	private Mono<ServerResponse> persistMessage(ServerRequest request) {
		//persist the message first then save feed
		return request
				.bodyToMono(Message.class)
				.flatMap(message -> messageServices.saveMessage(message))
				.flatMap(unused -> ServerResponse.ok().bodyValue(unused));
	}

	private Mono<ServerResponse> persistFeed(ServerRequest request) {
		//If it is a reply message, save it get the Id and add that to an existing message
		return request
				.bodyToMono(Feed.class)
				.flatMap(feed -> feedServices.saveFeed(feed))
				.flatMap(unused -> ServerResponse.ok().bodyValue(unused));

	}
}

@Service
@RequiredArgsConstructor
class FeedServices {
	final FeedRepository feedRepository;

	public Mono<Void> saveFeed(Feed feed) {
		return feedRepository
				.save(feed)
				.then();
	}
}

@Service
@RequiredArgsConstructor
class MessageServices {
	final MessageRepository messageRepository;

	public Mono<Void> saveMessage(Message message) {
		return messageRepository
				.save(message)
				.then();
	}

	public Mono<Void> saveReplyMessage(Message message, Message originalMessage) {
		return messageRepository
				.save(message)
				.flatMap(message1 -> null)
				.then();

	}

	public Mono<Message> updateMessage(String id, Message message) {
		return Mono.empty();
	}
}
