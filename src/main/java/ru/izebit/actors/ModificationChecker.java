package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import com.carrotsearch.hppc.LongDoubleHashMap;
import com.carrotsearch.hppc.LongDoubleMap;
import com.carrotsearch.hppc.cursors.LongDoubleCursor;
import ru.izebit.ApplicationLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

import static ru.izebit.ApplicationLauncher.Commands;
import static ru.izebit.ApplicationLauncher.Flags;

/**
 * Актор проверящий различия между текущий и прошлой версией объекта
 * После того, как приходит сообщение о том, что чтение из файла завершено
 * Из кэша берутся оставшившиеся необработанные значения.
 * <p/>
 * Created by Artem Konovalov on 9/5/15.
 */
public class ModificationChecker extends UntypedActor {
    public static final String NAME = "modification-checker";
    private LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private static final int INIT_CAPACITY = 13002000;

    private final String pathToOldFile;
    private final LongDoubleMap prevPrices;

    private int finishCompleteProducerCount = 0;
    private static final double REMOVED_ELEMENT = Double.NEGATIVE_INFINITY;

    public static Props props(final String pathToOldFile) {
        return Props.create(new Creator<ModificationChecker>() {
            private static final long serialVersionUID = 23L;

            @Override
            public ModificationChecker create() throws Exception {
                return new ModificationChecker(pathToOldFile);
            }
        });
    }

    public ModificationChecker(String pathToOldFile) {
        this.pathToOldFile = pathToOldFile;
        this.prevPrices = new LongDoubleHashMap(INIT_CAPACITY);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        try (Stream<String> lines = Files.lines(Paths.get(pathToOldFile))) {
            boolean isNextIdentity = true;
            String identity = null;

            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (isNextIdentity && line.startsWith(FileReader.ID_PREFIX)) {
                    identity = line.substring(FileReader.ID_PREFIX.length(), line.indexOf("\"", FileReader.ID_PREFIX.length()));
                    isNextIdentity = false;
                } else if (line.startsWith(FileReader.PRICE_PREFIX)) {
                    String price = line.substring(FileReader.PRICE_PREFIX.length(), line.indexOf("</", FileReader.PRICE_PREFIX.length()));
                    prevPrices.put(Long.parseLong(identity), Double.parseDouble(price));
                    isNextIdentity = true;
                }
            }

        } catch (IOException ex) {
            logger.error("ошибка инициализации", ex);
        }

        getContext().system().actorFor("user/" + FileReader.NAME).tell(ApplicationLauncher.Commands.START_READ, ActorRef.noSender());
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof String) {
            String msg = (String) message;
            int index = msg.indexOf(FileReader.DELIMITER_SYMBOL);
            long identity = Long.parseLong(msg.substring(0, index));
            double price = Double.parseDouble(msg.substring(index + 1, index = msg.lastIndexOf(FileReader.DELIMITER_SYMBOL)));

            String flags = msg.substring(index + 1);
            if (prevPrices.containsKey(identity)) {
                if (Double.compare(prevPrices.get(identity), price) != 0) {
                    flags += Flags.MODIFIED;
                }
                prevPrices.put(identity, REMOVED_ELEMENT);
            } else {
                flags += Flags.CREATED;
            }
            System.out.println(identity + " " + flags);

        } else if (message == Commands.FINISH_READ) {
            finishCompleteProducerCount++;
            if (finishCompleteProducerCount == UrlChecker.INSTANCE_COUNT) {

                Iterator<LongDoubleCursor> iterator = prevPrices.iterator();
                while (iterator.hasNext()) {
                    LongDoubleCursor entry = iterator.next();
                    if (entry.value == REMOVED_ELEMENT) {
                        System.out.println(entry.key + " "+Flags.REMOVED);
                    }
                }
                getContext().system().shutdown();
            }

        } else {
            unhandled(message);
        }
    }
}
