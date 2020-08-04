package xyz.jpenilla.announcerplus.config

import com.google.common.collect.ImmutableList
import com.okkero.skedule.CoroutineTask
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMapper
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.bukkit.Bukkit
import xyz.jpenilla.announcerplus.AnnouncerPlus
import xyz.jpenilla.announcerplus.Constants
import xyz.jpenilla.jmplib.Chat

@ConfigSerializable
class MessageConfig {
    companion object {
        private val MAPPER = ObjectMapper.forClass(MessageConfig::class.java)

        fun loadFrom(announcerPlus: AnnouncerPlus, node: CommentedConfigurationNode, name: String): MessageConfig {
            val temp = MAPPER.bindToNew().populate(node)
            temp.populate(announcerPlus, name)
            return temp
        }
    }

    fun saveTo(node: CommentedConfigurationNode) {
        MAPPER.bind(this).serialize(node)
    }

    fun populate(announcerPlus: AnnouncerPlus, name: String) {
        this.announcerPlus = announcerPlus
        this.chat = announcerPlus.chat
        this.name = name
    }

    private var broadcastTask: CoroutineTask? = null
    private lateinit var announcerPlus: AnnouncerPlus
    lateinit var name: String
    private lateinit var chat: Chat

    @Setting(value = "messages", comment = "The list of messages for a config")
    val messages = arrayListOf(
            Message(arrayListOf("<center><rainbow>Test AnnouncerPlus broadcast!")),
            Message(arrayListOf(
                    "{prefix1} <gradient:blue:green:blue>Multi-line test AnnouncerPlus broadcast",
                    "<center><gradient:red:gold:red>Line number two of three",
                    "{prefix1} <bold><rainbow>this is the last line (line 3)")),
            Message(arrayListOf("{prefix1} Test <gradient:blue:aqua>AnnouncerPlus</gradient> broadcast with sound<green>!"))
                    .sounds("minecraft:entity.strider.happy,minecraft:entity.villager.ambient,minecraft:block.note_block.cow_bell"),
            Message(arrayListOf("{prefix1} Use <click:run_command:/ap about><hover:show_text:'<rainbow>Click to run!'><rainbow>/ap about</rainbow></hover></click> to check the plugin version"))
                    .actionBar(ActionBarSettings(true, 15, "<{animate:pulse:red:blue:10}>Test Animated Action Bar Broadcast")),
            Message(arrayListOf("<bold><italic>Hello, </bold></italic> {nick} {prefix1} {r}!!!!!!!!!{rc}"))
                    .title(TitleSettings(1, 13, 2, "<gradient:green:blue:green:{animate:scroll:0.1}>||||||||||||||||||||||||||||||||||||||||||||", "<{animate:pulse:red:blue:10}>||||||||||||||||||||||||||||||||||||||||||")),
            Message(arrayListOf("<center><gradient:red:blue>Centered text Example"))
    )

    @Setting(value = "every-broadcast-commands", comment = "These commands will run as console once each interval\n  Example: \"broadcast This is a test\"")
    val commands = arrayListOf<String>()

    @Setting(value = "every-broadcast-per-player-commands", comment = "These commands will run as console once per player each interval\n  Example: \"minecraft:give %player_name% dirt\"")
    val perPlayerCommands = arrayListOf<String>()

    @Setting(value = "every-broadcast-as-player-commands", comment = "These commands will run once per player each interval, as the player\n  Example: \"ap about\"")
    val asPlayerCommands = arrayListOf<String>()

    @Setting(value = "interval-time-unit", comment = "The unit of time used for the interval\n  Can be SECONDS, MINUTES, or HOURS")
    var timeUnit = TimeUnit.MINUTES

    @Setting(value = "interval-time-amount", comment = "The amount of time used for the interval")
    var interval = 3

    @Setting(value = "random-message-order", comment = "Should the messages be sent in order of the config or in random order")
    var randomOrder = false

    fun broadcast() {
        stop()
        broadcastTask = announcerPlus.schedule(SynchronizationContext.ASYNC) {
            val tempMessages = messages
            if (randomOrder) {
                tempMessages.shuffle()
            }
            repeating(timeUnit.ticks * interval)
            for (message in tempMessages) {
                switchContext(SynchronizationContext.SYNC)
                val players = ImmutableList.copyOf(Bukkit.getOnlinePlayers())
                switchContext(SynchronizationContext.ASYNC)

                for (player in players) {
                    if (announcerPlus.essentials != null) {
                        if (announcerPlus.essentials!!.isAfk(player) && announcerPlus.perms!!.playerHas(player, "${announcerPlus.name}.messages.$name.afk")) {
                            continue
                        }
                    }
                    if (announcerPlus.perms!!.playerHas(player, "${announcerPlus.name}.messages.$name")) {
                        if (message.text.size != 0) {
                            chat.send(player, announcerPlus.configManager.parse(player, message.text))
                        }
                        chat.playSounds(player, message.randomSound, message.sounds)
                        if (message.actionBar.isEnabled()) {
                            message.actionBar.display(announcerPlus, player)
                        }
                        if (message.title.isEnabled()) {
                            message.title.display(announcerPlus, player)
                        }

                        switchContext(SynchronizationContext.SYNC)
                        for (command in message.perPlayerCommands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), announcerPlus.configManager.parse(player, command))
                        }
                        for (command in message.asPlayerCommands) {
                            Bukkit.dispatchCommand(player, announcerPlus.configManager.parse(player, command))
                        }
                        for (command in perPlayerCommands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), announcerPlus.configManager.parse(player, command))
                        }
                        for (command in asPlayerCommands) {
                            Bukkit.dispatchCommand(player, announcerPlus.configManager.parse(player, command))
                        }
                        switchContext(SynchronizationContext.ASYNC)
                    }
                }
                switchContext(SynchronizationContext.SYNC)
                for (command in message.commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), announcerPlus.configManager.parse(null, command))
                }
                for (command in commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), announcerPlus.configManager.parse(null, command))
                }
                switchContext(SynchronizationContext.ASYNC)
                yield()
            }
            broadcast()
        }
    }

    fun stop() {
        broadcastTask?.cancel()
    }

    @ConfigSerializable
    class Message {
        constructor()
        constructor(text: List<String>) {
            this.text.addAll(text)
        }

        @Setting(value = "message-text", comment = "The lines of text for this message. Can be empty for no chat messages.")
        val text = arrayListOf<String>()

        @Setting(value = "action-bar", comment = "Configure the Action Bar for this message")
        var actionBar = ActionBarSettings()

        @Setting(value = "title", comment = "Configure the Title for this message")
        var title = TitleSettings()

        @Setting(value = "sounds", comment = "The sounds to play when this message is sent\n  ${Constants.CONFIG_COMMENT_SOUNDS_LINE2}")
        var sounds = ""

        @Setting(value = "sounds-randomized", comment = Constants.CONFIG_COMMENT_SOUNDS_RANDOM)
        var randomSound = true

        @Setting(value = "commands", comment = "These commands will run as console on broadcast. Example: \"broadcast This is a test\"")
        val commands = arrayListOf<String>()

        @Setting(value = "per-player-commands", comment = "These commands will run as console once per player on broadcast. Example: \"minecraft:give %player_name% dirt\"")
        val perPlayerCommands = arrayListOf<String>()

        @Setting(value = "as-player-commands", comment = "These commands will run once per player, as the player on broadcast. Example: \"ap about\"")
        val asPlayerCommands = arrayListOf<String>()

        fun sounds(sounds: String): Message {
            this.sounds = sounds
            return this
        }

        fun actionBar(actionBar: ActionBarSettings): Message {
            this.actionBar = actionBar
            return this
        }

        fun title(title: TitleSettings): Message {
            this.title = title
            return this
        }
    }

    enum class TimeUnit(val ticks: Long) {
        SECONDS(20L),
        MINUTES(1200L),
        HOURS(72000L);
    }
}