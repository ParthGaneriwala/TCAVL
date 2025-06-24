# TCAVL
 Translation of a Cognitive Architecture for Verifiable Learning, Extended by Chinedum Ajabor, August 2024

 THe goal is to translate a Soar cognitive agent to its equivalent model in Uppaal model checker, so liveness and safety properties of the agent can be verified.

## Summer 2024 Files:
These are the files that I personally worked on and can speak on their origins and applications.

Counter.soar: Has soar rules to solve a simple counter problem. The goal is to count from 5 to 11. This is well translated and simulated by the translator with no syntax errors or logic errors

mac.soar: This is the original file, gotten from the Soar website tutorial to solve the missionaries and cannibals example. It has advanced constructs as well as preferences. This is not translated without syntax errors and hence cannot be simulated in Uppaal.

mac-rewritten.soar: Has soar rules to solve the missionaries and cannibals problem. However, these rules are gotten from mac.soar and rewritten to remove some constructs not supported by the translator. The reasons and some rules were rewritten are in comments before each rule that was rewritten. This translates with syntax errors relating to the rules that have preferences in them. These are the *select* rules. This translation does not simulate in Uppaal due to these syntax errors.

mac-rewritten-nopref.soar: This is mac-rewritten but without the specific select rules that cause syntax errors due to preferences present in those rules. This translates without syntax errors but the logic is not properly represented and hence does not simulate as expected. It would deadlock early. See report for update by references in the next steps section of report. Once update by reference is implemented, there will be some progress in getting this to simulate properly in Uppaal.

goalUppaalFile.xml: I manually edited the translated template from mac-rewritten-nopref.soar to update State_right_bank and State_left_bank any where State_operator_bank is updated. Also, nil was set to -999 instead of 0. These changes were to test how implementing update by reference would impact the accuracy of the simulation. We are able to break past the initial deadlock, but the simulation is not yet accurate.

## All other files

SoarTranslator: contains the translator source code. These are where my extensions to the translator are implemented.

Soar examples: contains some soar examples used in previous translator work

Translated soar examples: Contains translated uppaal templates from previous translator work


