document.addEventListener('DOMContentLoaded', function() {
    const iframe = document.getElementById('loginFrame'); // Assuming your iframe has this ID
    let checkAttempts = 0;
    const MAX_ATTEMPTS = 20;
    const CHECK_INTERVAL = 500000;

    function checkForLoginFormInIframe() {
        console.log('Checking for login form inside iframe...');

        if (iframe && iframe.contentDocument) {
            const iframeDoc = iframe.contentDocument;
            console.log('iframeDoc',iframeDoc);
            // Check if iframeDoc is blank (about:blank)
            if (iframeDoc.URL === 'about:blank') {
                
                // Wait for the iframe to load its content before checking again
                setTimeout(checkForLoginFormInIframe, CHECK_INTERVAL);
                return; // Exit this iteration of the function to wait for content to load
            }

            // Now look for elements within the iframe's document, assuming it's no longer blank
            let usernameField = iframeDoc.querySelector('input[name="username"], input[type="email"]');
            let passwordField = iframeDoc.querySelector('input[name="password"]');
            let submitButton = iframeDoc.querySelector('button[type="submit"], input[type="submit"]');
            let loginButton = Array.from(iframeDoc.querySelectorAll('button, input[type="submit"]')).find(button => button.textContent.trim().toLowerCase().includes("login"));

            if (usernameField && passwordField && (submitButton || loginButton)) {
                console.log('Login form found inside iframe. Filling credentials...');
                usernameField.value = exivityUsername;
                passwordField.value = exivityPassword;
                
                // Choose the appropriate button to click
                if (submitButton) {
                    submitButton.click();
                } else if (loginButton) {
                    loginButton.click();
                }
                
                console.log('Login attempt inside iframe completed.');
                return; // Stop checking once login is attempted
            } else {
                // Handle not found elements or retry logic here
                checkAttempts++;
                if (checkAttempts < MAX_ATTEMPTS) {
                    console.log(`Login form elements not found in iframe yet. Attempt ${checkAttempts} of ${MAX_ATTEMPTS}. Retrying...`);
                    setTimeout(checkForLoginFormInIframe, CHECK_INTERVAL);
                } else {
                    console.error('Login form not found in iframe after maximum attempts. Stopping check.');
                }
            }
        } else {
            // Handle iframe not loaded or accessible
            checkAttempts++;
            if (checkAttempts < MAX_ATTEMPTS) {
                console.log(`Iframe or its content not accessible yet. Attempt ${checkAttempts} of ${MAX_ATTEMPTS}. Retrying...`);
                setTimeout(checkForLoginFormInIframe, CHECK_INTERVAL);
            } else {
                console.error('Failed to access iframe or its content after maximum attempts. Stopping check.');
            }
        }
    }

    // Start checking for the login form inside the iframe
    checkForLoginFormInIframe();
});