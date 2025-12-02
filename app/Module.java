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

    }

    @Singleton
    public static class SourcesActorProvider implements Provider<ActorRef<SourcesActor.GetSources>> {
        private final ActorSystem actorSystem;

        @Inject
        public SourcesActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @Override
        public ActorRef<SourcesActor.GetSources> get() {
            return Adapter.spawn(
                    actorSystem,
                    SourcesActor.create(),
                    "sourcesActor");
        }
    }

    @Singleton
    public static class QueryResultActorProvider implements Provider<ActorRef<QueryResultActor.Message>> {
        private final ActorSystem actorSystem;

        @Inject
        public QueryResultActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @Override
        public ActorRef<QueryResultActor.Message> get() {
            return Adapter.spawn(
                    actorSystem,
                    QueryResultActor.create(),
                    "queryResultActor");
        }
    }


    @Singleton
    public static class UserParentActorProvider implements Provider<ActorRef<UserParentActor.Create>> {
        private final ActorSystem actorSystem;
        private final UserActor.Factory childFactory;
        private final Config config;

        @Inject
        public UserParentActorProvider(
                ActorSystem actorSystem, UserActor.Factory childFactory, Config config
        ) {
            this.actorSystem = actorSystem;
            this.childFactory = childFactory;
            this.config = config;
        }

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

    @Singleton
    public static class UserActorFactoryProvider implements Provider<UserActor.Factory> {
        private final ActorRef<SourcesActor.GetSources> sourcesActor;
        private final WSClient ws;
        private final Config config;
        private final ActorRef<ReadabilityActor.Command> readabilityActor;
        private final ActorRef<CacheActor.Command> cacheActor;

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

        @Override
        public UserActor.Factory get() {
            return id -> UserActor.create(id, sourcesActor, readabilityActor, cacheActor, ws, config);
        }
    }

    // New provider for ReadabilityActor
    @Singleton
    public static class ReadabilityActorProvider implements Provider<ActorRef<ReadabilityActor.Command>> {
        private final ActorSystem actorSystem;

        @Inject
        public ReadabilityActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @Override
        public ActorRef<ReadabilityActor.Command> get() {
            // Spawn a top-level ReadabilityActor. It can be used as a singleton service.
            return Adapter.spawn(
                    actorSystem,
                    ReadabilityActor.create(),
                    "readabilityActor");
        }
    }

    @Singleton
    public static class StatisticsActorProvider implements Provider<ActorRef<StatisticsActor.Command>> {
        private final ActorSystem actorSystem;

        @Inject
        public StatisticsActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @Override
        public ActorRef<StatisticsActor.Command> get() {
            return Adapter.spawn(
                    actorSystem,
                    StatisticsActor.create(),
                    "statisticsActor"
            );
        }
    }

    @Singleton
    public static class CacheActorProvider implements Provider<ActorRef<CacheActor.Command>> {

        private final ActorSystem actorSystem;

        @Inject
        public CacheActorProvider(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @Override
        public ActorRef<CacheActor.Command> get() {
            return Adapter.spawn(
                    actorSystem,
                    CacheActor.create(),
                    "cacheActor"
            );
        }
    }

}