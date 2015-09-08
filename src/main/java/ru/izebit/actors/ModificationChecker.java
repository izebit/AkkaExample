package ru.izebit.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.routing.RoundRobinPool;
import com.carrotsearch.hppc.LongDoubleHashMap;
import com.carrotsearch.hppc.LongDoubleMap;
import com.carrotsearch.hppc.cursors.LongDoubleCursor;
import ru.izebit.ApplicationLauncher;
import ru.izebit.Offer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
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

    private final String pathToOldFile;
    private final ActorRef urlChecker;

    private final LongDoubleMap prevPriceCache;
    private static final int INIT_CAPACITY = 13002000;
    private static final double REMOVED_ELEMENT = Double.NEGATIVE_INFINITY;

    private long currentTime = -1;

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
        this.prevPriceCache = new LongDoubleHashMap(INIT_CAPACITY);
        this.urlChecker = getContext().actorOf(Props.create(UrlChecker.class)
                .withRouter(new RoundRobinPool(UrlChecker.INSTANCE_COUNT))
                .withMailbox("akka.actor.my-bounded-mailbox"), UrlChecker.NAME);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        try (Stream<String> lines = Files.lines(Paths.get(pathToOldFile))) {
            Iterator<String> iterator = lines.iterator();

            Unmarshaller unmarshaller = JAXBContext.newInstance(Offer.class).createUnmarshaller();
            boolean isNextOpenTag = true;
            StringBuilder context = null;
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (isNextOpenTag) {
                    if (line.trim().startsWith(FileReader.OPEN_TAG)) {
                        context = new StringBuilder();
                        context.append(line);

                        isNextOpenTag = false;
                    }
                } else {
                    context.append(line);
                    if (line.trim().startsWith(FileReader.CLOSE_TAG)) {
                        Offer offer = (Offer) unmarshaller.unmarshal(new StringReader(context.toString()));
                        prevPriceCache.put(offer.getId(), offer.getPrice());
                        isNextOpenTag = true;
                    }
                }
            }

        } catch (IOException ex) {
            logger.error("ошибка инициализации", ex);
        }

        getContext().system().actorFor("user/" + FileReader.NAME).tell(ApplicationLauncher.Commands.START_READ, ActorRef.noSender());
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof Offer) {
            Offer offer = (Offer) message;
            long id = offer.getId();
            double price = offer.getPrice();

            if (prevPriceCache.containsKey(id)) {
                if (Double.compare(prevPriceCache.get(id), price) != 0) {
                    offer.setCheckResults(offer.getCheckResults() + Flags.MODIFIED);
                }
                prevPriceCache.put(id, REMOVED_ELEMENT);
            } else {
                offer.setCheckResults(offer.getCheckResults() + Flags.CREATED);
            }
            urlChecker.tell(offer, ActorRef.noSender());

        } else if (message == Commands.FINISH_READ) {

            Iterator<LongDoubleCursor> iterator = prevPriceCache.iterator();
            while (iterator.hasNext()) {
                LongDoubleCursor entry = iterator.next();
                if (entry.value == REMOVED_ELEMENT) {
                    System.out.println(entry.key + " " + Flags.REMOVED);
                }
            }
            urlChecker.tell(Commands.FINISH_READ, ActorRef.noSender());

        } else {
            unhandled(message);
        }
    }
}
