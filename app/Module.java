import actors.*;
import models.QueryResult;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import play.libs.ws.WSClient;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.typesafe.config.Config;
import com.google.inject.name.Names;
import play.libs.pekko.PekkoGuiceSupport;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@SuppressWarnings("unused")
public class Module extends AbstractModule implements PekkoGuiceSupport {
    /**
     * Configure the module by binding the actors
     * @author Team
     */
    @Override
    protected void configure() {
        bind(new TypeLiteral<ActorRef<SourcesActor.GetSources>>() {})
                .toProvider(SourcesActorProvider.class)
                .asEagerSingleton();
        bind(new TypeLiteral<ActorRef<UserParentActor.Create>>() {})
                .toProvider(UserParentActorProvider.class)
                .asEagerSingleton();
        bind(UserActor.Factory.class).toProvider(UserActorFactoryProvider.class);

        bind(new TypeLiteral<ActorRef<QueryResultActor.Message>>() {})
                .toProvider(QueryResultActorProvider.class)
                .asEagerSingleton();

        // Bind a provider for the ReadabilityActor so it can be injected elsewhere
        bind(new TypeLiteral<ActorRef<ReadabilityActor.Command>>() {})
                .toProvider(ReadabilityActorProvider.class)
                .asEagerSingleton();

        //Stat actor and cache actor that communicates with it
        bind(new TypeLiteral<ActorRef<StatisticsActor.Command>>() {})
                .toProvider(StatisticsActorProvider.class)
                .asEagerSingleton();

        bind(new TypeLiteral<ActorRef<CacheActor.Command>>() {})
                .toProvider(CacheActorProvider.class)
                .asEagerSingleton();


        bind(new TypeLiteral<ActorRef<SourceProfileActor.Command>>() {})
                .toProvider(SourceProfileActorProvider.class)
                .asEagerSingleton();
    }

    /**
     * Provider class for the actors
     * @author ihasnaou
     */
    @Singleton
    public static class SourcesActorProvider implements Provider<ActorRef<SourcesActor.GetSources>> {
        private final ActorSystem actorSystem;

        /**
         * Constructor for the provider class
         * @author ihasnaou
         */
        @Inject
        public SourcesActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        /**
         * getter for the actorSystem
         * @author ihasnaou
         */
        @Override
        public ActorRef<SourcesActor.GetSources> get() {
            return Adapter.spawn(
                    actorSystem,
                    SourcesActor.create(),
                    "sourcesActor");
        }
    }

    /**
     * Class for querying
     * @author ihasnaou
     */
    @Singleton
    public static class QueryResultActorProvider implements Provider<ActorRef<QueryResultActor.Message>> {
        private final ActorSystem actorSystem;

        /**
         * Constructor for the query provider
         * @author ihasnaou
         */
        @Inject
        public QueryResultActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        /**
         * getter for the query provider
         * @author ihasnaou
         */
        @Override
        public ActorRef<QueryResultActor.Message> get() {
            return Adapter.spawn(
                    actorSystem,
                    QueryResultActor.create(),
                    "queryResultActor");
        }
    }


    /**
     * Class for the User Parent Actor
     * @author ihasnaou
     */
    @Singleton
    public static class UserParentActorProvider implements Provider<ActorRef<UserParentActor.Create>> {
        private final ActorSystem actorSystem;
        private final UserActor.Factory childFactory;
        private final Config config;

        /**
         * gConstructor
         * @author ihasnaou
         */
        @Inject
        public UserParentActorProvider(
                ActorSystem actorSystem, UserActor.Factory childFactory, Config config
        ) {
            this.actorSystem = actorSystem;
            this.childFactory = childFactory;
            this.config = config;
        }

        /**
         * getter for the actorSystem
         * @author ihasnaou
         */
        @Override
        public ActorRef<UserParentActor.Create> get() {
            Behavior<UserParentActor.Create> supervised = Behaviors.supervise(
                    UserParentActor.create(childFactory, config)
            ).onFailure(SupervisorStrategy.restart());
            return Adapter.spawn(
                    actorSystem,
                    supervised,
                    "userParentActor");
        }
    }

    /**
     * Factory provider class
     * @author ihasnaou
     */
    @Singleton
    public static class UserActorFactoryProvider implements Provider<UserActor.Factory> {
        private final ActorRef<SourcesActor.GetSources> sourcesActor;
        private final WSClient ws;
        private final Config config;
        private final ActorRef<ReadabilityActor.Command> readabilityActor;
        private final ActorRef<CacheActor.Command> cacheActor;

        /**
         * Constructor for Factory Actor provider
         * @author ihasnaou
         */
        @Inject
        public UserActorFactoryProvider(ActorRef<SourcesActor.GetSources> sourcesActor,
                                        ActorRef<ReadabilityActor.Command> readabilityActor,
                                        ActorRef<CacheActor.Command> cacheActor,
                                        WSClient ws, Config config) {
            this.sourcesActor = sourcesActor;
            this.readabilityActor = readabilityActor;
            this.cacheActor = cacheActor;
            this.ws = ws;
            this.config = config;
        }

        /**
         * getter for the factory provider
         * @author ihasnaou
         */
        @Override
        public UserActor.Factory get() {
            return id -> UserActor.create(id, sourcesActor, readabilityActor, cacheActor, ws, config);
        }
    }

    /**
     * Readability provider class
     * @author SD
     */
    // New provider for ReadabilityActor
    @Singleton
    public static class ReadabilityActorProvider implements Provider<ActorRef<ReadabilityActor.Command>> {
        private final ActorSystem actorSystem;

        /**
         * Constructor for the Readability Provider
         * @author SD
         */
        @Inject
        public ReadabilityActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        /**
         * getter for the Readability Provider
         * @author SD
         */
        @Override
        public ActorRef<ReadabilityActor.Command> get() {
            // Spawn a top-level ReadabilityActor. It can be used as a singleton service.
            return Adapter.spawn(
                    actorSystem,
                    ReadabilityActor.create(),
                    "readabilityActor");
        }
    }

    /**
     * Statistics provider Class
     * @author Karim BG
     */
    @Singleton
    public static class StatisticsActorProvider implements Provider<ActorRef<StatisticsActor.Command>> {
        private final ActorSystem actorSystem;

        /**
         * Constructor for the statistics Provider
         * @author Karim BG
         */
        @Inject
        public StatisticsActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        /**
         * getter for the Statistics Provider
         * @author SD
         */
        @Override
        public ActorRef<StatisticsActor.Command> get() {
            return Adapter.spawn(
                    actorSystem,
                    StatisticsActor.create(),
                    "statisticsActor"
            );
        }
    }

    /**
     * Cache Actor provider Class
     * @author Karim BG
     */
    @Singleton
    public static class CacheActorProvider implements Provider<ActorRef<CacheActor.Command>> {

        private final ActorSystem actorSystem;

        /**
         * Constructor for the statistics Provider
         * @author Karim BG
         */
        @Inject
        public CacheActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        /**
         * getter for the statistics Provider
         * @author Karim BG
         */
        @Override
        public ActorRef<CacheActor.Command> get() {
            return Adapter.spawn(
                    actorSystem,
                    CacheActor.create(),
                    "cacheActor"
            );
        }
    }


    /**
     * Source Profile Actor provider Class
     * @author Haytham
     */
    @Singleton
    public static class SourceProfileActorProvider implements Provider<ActorRef<SourceProfileActor.Command>> {
        private final ActorSystem actorSystem;
        private final WSClient ws;
        private final Config config;

        /**
         * Constructor for the class
         * @author Haytham
         */
        @Inject
        public SourceProfileActorProvider(ActorSystem actorSystem, WSClient ws, Config config) {
            this.actorSystem = actorSystem;
            this.ws = ws;
            this.config = config;
        }
        /**
         * getter for the class
         * @author Haytham
         */
        @Override
        public ActorRef<SourceProfileActor.Command> get() {

            String baseUrl = config.getString("newsapi.url");
            String apiKey  = config.getString("newsapi.key");

            return Adapter.spawn(
                    actorSystem,
                    SourceProfileActor.create(baseUrl, apiKey, ws),
                    "SourceProfileActor");
        }
    }
}