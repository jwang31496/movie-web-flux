package com.gomezrondon.moviewebflux.configuration;


import com.gomezrondon.moviewebflux.entity.Movie;
import com.gomezrondon.moviewebflux.service.MovieService;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class ConfigApplication {

    Logger log = org.slf4j.LoggerFactory.getLogger(ConfigApplication.class);

    private final MovieService service;
    private final ConnectionFactory connectionFactory;

    public ConfigApplication(MovieService service, @Qualifier("connectionFactory") ConnectionFactory connectionFactory) {
        this.service = service;
        this.connectionFactory = connectionFactory;
    }


    @Bean
    @Profile("prod")
    CommandLineRunner bootCLRProd(){
        return args -> {
            System.out.println("This is Production!");
        };
    }

    @Bean
    @Profile("dev")
    CommandLineRunner bootCLR(){
        return args -> {
            Flux<? extends Result> dropTable = Mono.from(connectionFactory.create())
                    .flatMapMany(connection -> connection
                            .createStatement("DROP TABLE IF EXISTS movie;")
                         //   .createStatement("DROP TABLE movie;") // for testing error handling
                            .execute());


            Flux<? extends Result> createTable = Mono.from(connectionFactory.create())
                    .flatMapMany(connection -> connection
                            .createStatement(
                                    "" +
                                            "create table movie\n" +
                                            "(\n" +
                                            "    id   int auto_increment  PRIMARY KEY,\n" +
                                            "    name varchar(50) not null\n" +
                                            ");"
                            ).execute());


             Flux<Movie> movieFlux = getMovieFlux(List.of("Matrix","Terminator", "RoboCop", "Alien II", "RoboCop2","Batman Begins ", "Matrix 2", "Transformers", "Limitless"));

            dropTable
                    .log("**dropTable: ")
                    .doOnError(e -> log.error("error message: {}", e.getMessage()))
                    .thenMany(createTable.log("**Create table: "))
                    .onErrorResume(s -> {
                        log.info("inside on Error Resume");
                        return createTable.then(Mono.empty());
                    })
                    .thenMany(service.deleteAll())    // delete all records
                    .thenMany(movieFlux)             // insert all records
                    //.thenMany(service.findAll()) 	// find all records
                    .doOnNext(System.out::println)
                    .blockLast()//block to allow it to finish before the test cases start
            ;
        };
    }



    @NotNull
    private Flux<Movie> getMovieFlux(List<String> list) {
        return Flux.fromIterable(list)
                .map(String::toLowerCase)
                .map(title -> new Movie(null, title))
                .flatMap(movie -> {
                    if (movie.getName().equals("matrix")) {
                        movie.setId(1);
                        return service.insert(movie);
                    } else {
                        return service.save(movie);
                    }
                });
    }

}// fin de Config
