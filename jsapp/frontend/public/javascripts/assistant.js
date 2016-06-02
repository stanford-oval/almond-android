$(function() {
    var ws = new WebSocket('ws://127.0.0.1:3000/assistant/socket');
    ws.onmessage = function(messageEvent) {
        var msg = JSON.parse(messageEvent.data);

        if (msg.text)
            showText(msg.text, 'sabrina-says');
        else if (msg.picture)
            showPicture(msg.picture, 'sabrina-says');
        else if (msg.choice)
            showButton(msg.button, 'sabrina-says');
        else if (msg.rdl)
            showRDL(msg.rdl, 'sabrina-says');
    };

    var placeholder = $('#sabrina-placeholder');

    function showText(text, withClass) {
        placeholder.append($('<p>').addClass(withClass).text(text));
    }

    function showPicture(url, withClass) {
        placeholder.append($('<img>').addClass(withClass).attr('src', url));
    }

    function showButton() {
        // FINISHME
    }

    function showRDL() {
        // FINISHME
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
