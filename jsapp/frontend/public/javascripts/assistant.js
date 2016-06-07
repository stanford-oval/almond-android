$(function() {
    var ws = new WebSocket('ws://127.0.0.1:3000/assistant/socket');
    ws.onmessage = function(messageEvent) {
        var msg = JSON.parse(messageEvent.data);

        var _class = msg.from === 'user' ? 'user-says' : 'sabrina-says';

        if (msg.text)
            showText(msg.text, _class);
        else if (msg.picture)
            showPicture(msg.picture, _class);
        else if (msg.choice)
            showButton(msg.button, _class);
        else if (msg.rdl)
            showRDL(msg.rdl, _class);
        else if (msg.link)
            showLink(msg.link, _class);
    };

    var placeholder = $('#sabrina-placeholder');

    function showText(text, withClass) {
        placeholder.append($('<p>').addClass(withClass).text(text));
    }

    function showPicture(url, withClass) {
        placeholder.append($('<img>').addClass(withClass).attr('src', url));
    }

    function showButton(button, withClass) {
        placeholder.append($('<a>')
            .addClass('btn btn-default').addClass(withClass)
            .text(button.title).click(function() {
                sendChoice(button.id);
            }));
    }

    function showRDL() {
        // FINISHME
    }

    function showLink(link, withClass) {
        placeholder.append($('<a>').addClass(withClass)
            .text(link.title).attr('href', link.url));
    }

    function sendChoice(idx) {
        sendMsg({ type: "Choice", value: hash }, true);
    }

    function sendMsg(text, hidden) {
        ws.send(JSON.stringify({ type: 'text', hidden: hidden, text: text }));
    }

    $('#input-form').submit(function(event) {
        event.preventDefault();

        var text = $('#input-text').val();
        showText(text, 'user-says');
        sendMsg(text);
        $('#input-text').val('');
    });
})
