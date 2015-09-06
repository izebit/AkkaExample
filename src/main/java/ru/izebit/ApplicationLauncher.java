package ru.izebit;

import akka.actor.ActorSystem;
import ru.izebit.actors.FileReader;
import ru.izebit.actors.ModificationChecker;

/**
 * Стартовая точка
 * <p/>
 * Created by Artem Konovalov on 9/5/15.
 */
public class ApplicationLauncher {
    
    public static void main(String[] args) throws Exception {
        ActorSystem actorSystem = ActorSystem.create("my-universe");
        actorSystem.actorOf(FileReader.props(args[0]).withDispatcher("akka.actor.my-thread-pool-dispatcher"), FileReader.NAME);
        actorSystem.actorOf(ModificationChecker.props(args[1]), ModificationChecker.NAME);
    }

    public static enum Commands {
        START_READ,
        FINISH_READ
    }

    public static enum Flags {
        MODIFIED("m"),
        REMOVED("r"),
        CREATED("n"),
        PICTURE_IS_NOT_AVAILABLE("p");

        public final String name;

        private Flags(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

